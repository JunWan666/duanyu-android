/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.api

import com.google.ai.edge.gallery.coreai.DuanYuAiService
import java.io.BufferedReader
import java.io.InputStreamReader
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
  private val scope: CoroutineScope,
) {
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
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine().orEmpty()
        val route = requestLine.toRoute()
        while (!reader.readLine().isNullOrEmpty()) {
          // Drain headers for this first local skeleton.
        }

        val response =
          when (route) {
            "GET /health" ->
              HttpResponse(
                status = "200 OK",
                body = """{"status":"ok","service":"duanyu-api"}""",
              )
            "GET /v1/models" ->
              HttpResponse(
                status = "200 OK",
                body = modelsJson(),
              )
            else ->
              HttpResponse(
                status = "404 Not Found",
                body =
                  """{"error":{"message":"Endpoint is not implemented yet.","type":"not_found"}}""",
              )
          }
        client.getOutputStream().writeResponse(response)
      }
    }
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

  private fun OutputStream.writeResponse(response: HttpResponse) {
    val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
    val header =
      buildString {
        append("HTTP/1.1 ").append(response.status).append("\r\n")
        append("Content-Type: application/json; charset=utf-8\r\n")
        append("Content-Length: ").append(bodyBytes.size).append("\r\n")
        append("Connection: close\r\n")
        append("\r\n")
      }
    write(header.toByteArray(StandardCharsets.UTF_8))
    write(bodyBytes)
    flush()
  }

  private data class HttpResponse(val status: String, val body: String)

  private companion object {
    const val BACKLOG = 8
  }
}
