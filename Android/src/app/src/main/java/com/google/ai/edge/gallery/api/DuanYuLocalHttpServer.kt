/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.api

import com.google.ai.edge.gallery.coreai.DuanYuChatCompletionChunk
import com.google.ai.edge.gallery.coreai.DuanYuChatCompletionRequest
import com.google.ai.edge.gallery.coreai.DuanYuChatMessage
import com.google.ai.edge.gallery.coreai.DuanYuAiService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DuanYuLocalHttpServer(
  private val host: String,
  private val port: Int,
  private val aiService: DuanYuAiService,
  private val apiTokenProvider: () -> String,
  private val scope: CoroutineScope,
) {
  private val gson = Gson()
  private val running = AtomicBoolean(false)
  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null

  fun start(onStarted: () -> Unit, onError: (String) -> Unit) {
    if (!running.compareAndSet(false, true)) {
      onStarted()
      return
    }

    serverJob =
      scope.launch(Dispatchers.IO) {
        try {
          ServerSocket(port, BACKLOG, InetAddress.getByName(host)).use { socket ->
            serverSocket = socket
            onStarted()
            while (running.get()) {
              val client = socket.accept()
              launch { handleClient(client) }
            }
          }
        } catch (e: Exception) {
          if (running.get()) {
            onError(e.message ?: "Failed to start local API service.")
          }
        } finally {
          running.set(false)
          serverSocket = null
        }
      }
  }

  fun stop() {
    running.set(false)
    serverSocket?.close()
    serverJob?.cancel()
    serverSocket = null
    serverJob = null
  }

  private suspend fun handleClient(socket: Socket) {
    withContext(Dispatchers.IO) {
      socket.use { client ->
        val inputStream = client.getInputStream()
        val requestLine = inputStream.readHttpLine().orEmpty()
        val route = requestLine.toRoute()
        val headers = mutableMapOf<String, String>()
        while (true) {
          val line = inputStream.readHttpLine()
          if (line.isNullOrEmpty()) {
            break
          }
          val separatorIndex = line.indexOf(':')
          if (separatorIndex > 0) {
            headers[line.substring(0, separatorIndex).trim().lowercase(Locale.US)] =
              line.substring(separatorIndex + 1).trim()
          }
        }
        val body = inputStream.readBody(headers.contentLength())

        if (route != "GET /health" && !headers.hasValidBearerToken(apiTokenProvider())) {
          client.getOutputStream()
            .writeJsonResponse(
              HttpResponse(
                status = "401 Unauthorized",
                headers = listOf("WWW-Authenticate: Bearer"),
                body =
                  errorJson(
                    message = "Missing or invalid API token.",
                    type = "authentication_error",
                  ),
              )
            )
          return@use
        }

        when (route) {
          "GET /health" ->
            client.getOutputStream()
              .writeJsonResponse(
                HttpResponse(
                  status = "200 OK",
                  body = """{"status":"ok","service":"duanyu-api"}""",
                )
              )
          "GET /v1/models" ->
            client.getOutputStream()
              .writeJsonResponse(
                HttpResponse(
                  status = "200 OK",
                  body = modelsJson(),
                )
              )
          "POST /v1/chat/completions" ->
            handleChatCompletion(
              body = body,
              outputStream = client.getOutputStream(),
            )
          else ->
            client.getOutputStream()
              .writeJsonResponse(
                HttpResponse(
                  status = "404 Not Found",
                  body =
                    errorJson(
                      message = "Endpoint is not implemented yet.",
                      type = "not_found",
                    ),
                )
              )
        }
      }
    }
  }

  private suspend fun handleChatCompletion(body: String, outputStream: OutputStream) {
    val parsedRequest =
      try {
        parseChatCompletionRequest(body)
      } catch (e: IllegalArgumentException) {
        outputStream.writeJsonResponse(
          HttpResponse(
            status = "400 Bad Request",
            body = errorJson(e.message ?: "Invalid request.", "invalid_request_error"),
          )
        )
        return
      }

    try {
      if (parsedRequest.request.stream) {
        outputStream.writeSseHeaders()
        aiService.chatCompletion(parsedRequest.request) { chunk ->
          outputStream.writeSseChunk(
            id = parsedRequest.responseId,
            model = parsedRequest.modelId,
            chunk = chunk,
          )
        }
        outputStream.writeSseDone()
      } else {
        val content = StringBuilder()
        aiService.chatCompletion(parsedRequest.request) { chunk -> content.append(chunk.content) }
        outputStream.writeJsonResponse(
          HttpResponse(
            status = "200 OK",
            body =
              chatCompletionJson(
                id = parsedRequest.responseId,
                model = parsedRequest.modelId,
                content = content.toString(),
              ),
          )
        )
      }
    } catch (e: Exception) {
      outputStream.writeJsonResponse(
        HttpResponse(
          status = "500 Internal Server Error",
          body = errorJson(e.message ?: "Failed to run chat completion.", "server_error"),
        )
      )
    }
  }

  private fun parseChatCompletionRequest(body: String): ParsedChatCompletionRequest {
    if (body.isBlank()) {
      throw IllegalArgumentException("Request body must not be empty.")
    }
    val payload =
      try {
        gson.fromJson(body, OpenAiChatCompletionRequest::class.java)
      } catch (e: JsonSyntaxException) {
        throw IllegalArgumentException("Request body is not valid JSON.")
      } ?: throw IllegalArgumentException("Request body is not valid JSON.")

    val modelId = payload.model?.trim().orEmpty()
    if (modelId.isEmpty()) {
      throw IllegalArgumentException("Field 'model' is required.")
    }
    val messages =
      payload.messages.orEmpty().mapIndexed { index, message ->
        val role = message.role?.trim().orEmpty()
        val content = message.content?.trim().orEmpty()
        if (role.isEmpty()) {
          throw IllegalArgumentException("Message $index is missing role.")
        }
        if (content.isEmpty()) {
          throw IllegalArgumentException("Message $index is missing content.")
        }
        DuanYuChatMessage(role = role, content = content)
      }
    if (messages.isEmpty()) {
      throw IllegalArgumentException("Field 'messages' must contain at least one message.")
    }

    val responseId = "chatcmpl-${System.currentTimeMillis()}"
    return ParsedChatCompletionRequest(
      responseId = responseId,
      modelId = modelId,
      request =
        DuanYuChatCompletionRequest(
          modelId = modelId,
          messages = messages,
          stream = payload.stream == true,
        ),
    )
  }

  private fun modelsJson(): String {
    val models =
      aiService.listModels().joinToString(separator = ",") { model ->
        val id = model.id.jsonEscaped()
        val displayName = model.displayName.jsonEscaped()
        val ownedBy = "duanyu"
        """{"id":"$id","object":"model","owned_by":"$ownedBy","display_name":"$displayName"}"""
      }
    return """{"object":"list","data":[$models]}"""
  }

  private fun chatCompletionJson(id: String, model: String, content: String): String {
    val now = System.currentTimeMillis() / 1000
    return """
      {"id":"${id.jsonEscaped()}","object":"chat.completion","created":$now,"model":"${model.jsonEscaped()}","choices":[{"index":0,"message":{"role":"assistant","content":"${content.jsonEscaped()}"},"finish_reason":"stop"}]}
    """
      .trimIndent()
  }

  private fun chatCompletionChunkJson(
    id: String,
    model: String,
    content: String,
    done: Boolean,
  ): String {
    val now = System.currentTimeMillis() / 1000
    val finishReason = if (done) """"stop"""" else "null"
    return """
      {"id":"${id.jsonEscaped()}","object":"chat.completion.chunk","created":$now,"model":"${model.jsonEscaped()}","choices":[{"index":0,"delta":{"content":"${content.jsonEscaped()}"},"finish_reason":$finishReason}]}
    """
      .trimIndent()
  }

  private fun errorJson(message: String, type: String): String {
    return """{"error":{"message":"${message.jsonEscaped()}","type":"${type.jsonEscaped()}"}}"""
  }

  private fun Map<String, String>.contentLength(): Int =
    this["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

  private fun Map<String, String>.hasValidBearerToken(expectedToken: String): Boolean {
    val authHeader = this["authorization"].orEmpty()
    if (!authHeader.startsWith(BEARER_PREFIX, ignoreCase = true)) {
      return false
    }
    val token = authHeader.substring(BEARER_PREFIX.length).trim()
    return expectedToken.isNotBlank() && token == expectedToken
  }

  private fun InputStream.readHttpLine(): String? {
    val lineBytes = ByteArrayOutputStream()
    while (true) {
      val byte = read()
      if (byte == -1) {
        return if (lineBytes.size() == 0) null else lineBytes.toString(StandardCharsets.UTF_8.name())
      }
      if (byte == '\n'.code) {
        return lineBytes.toString(StandardCharsets.UTF_8.name()).trimEnd('\r')
      }
      lineBytes.write(byte)
    }
  }

  private fun InputStream.readBody(contentLength: Int): String {
    if (contentLength <= 0) {
      return ""
    }
    val buffer = ByteArray(contentLength)
    var offset = 0
    while (offset < contentLength) {
      val read = read(buffer, offset, contentLength - offset)
      if (read < 0) {
        break
      }
      offset += read
    }
    return String(buffer, 0, offset, StandardCharsets.UTF_8)
  }

  private fun String.toRoute(): String {
    val parts = trim().split(" ")
    if (parts.size < 2) {
      return ""
    }
    return "${parts[0].uppercase(Locale.US)} ${parts[1].substringBefore("?")}"
  }

  private fun String.jsonEscaped(): String {
    return buildString {
      for (char in this@jsonEscaped) {
        when (char) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\b' -> append("\\b")
          '\u000C' -> append("\\f")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> append(char)
        }
      }
    }
  }

  private fun OutputStream.writeJsonResponse(response: HttpResponse) {
    val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
    val header =
      buildString {
        append("HTTP/1.1 ").append(response.status).append("\r\n")
        response.headers.forEach { append(it).append("\r\n") }
        append("Content-Type: application/json; charset=utf-8\r\n")
        append("Content-Length: ").append(bodyBytes.size).append("\r\n")
        append("Connection: close\r\n")
        append("\r\n")
      }
    write(header.toByteArray(StandardCharsets.UTF_8))
    write(bodyBytes)
    flush()
  }

  private fun OutputStream.writeSseHeaders() {
    val header =
      buildString {
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: text/event-stream; charset=utf-8\r\n")
        append("Cache-Control: no-cache\r\n")
        append("Connection: close\r\n")
        append("\r\n")
      }
    write(header.toByteArray(StandardCharsets.UTF_8))
    flush()
  }

  private fun OutputStream.writeSseChunk(
    id: String,
    model: String,
    chunk: DuanYuChatCompletionChunk,
  ) {
    val json =
      chatCompletionChunkJson(
        id = id,
        model = model,
        content = chunk.content,
        done = chunk.done,
      )
    write("data: $json\n\n".toByteArray(StandardCharsets.UTF_8))
    flush()
  }

  private fun OutputStream.writeSseDone() {
    write("data: [DONE]\n\n".toByteArray(StandardCharsets.UTF_8))
    flush()
  }

  private data class HttpResponse(
    val status: String,
    val body: String,
    val headers: List<String> = emptyList(),
  )

  private data class ParsedChatCompletionRequest(
    val responseId: String,
    val modelId: String,
    val request: DuanYuChatCompletionRequest,
  )

  private data class OpenAiChatCompletionRequest(
    val model: String?,
    val messages: List<OpenAiChatMessage>?,
    val stream: Boolean?,
  )

  private data class OpenAiChatMessage(
    val role: String?,
    val content: String?,
  )

  private companion object {
    const val BACKLOG = 8
    const val BEARER_PREFIX = "Bearer "
  }
}
