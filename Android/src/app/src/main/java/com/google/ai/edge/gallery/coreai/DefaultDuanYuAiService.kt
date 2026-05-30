/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.coreai

import android.content.Context
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class DefaultDuanYuAiService
@Inject
constructor(
  private val modelRegistry: DuanYuModelRegistry,
  private val runtimeModelRegistry: DuanYuRuntimeModelRegistry,
  @param:ApplicationContext private val context: Context,
) : DuanYuAiService {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val inferenceMutex = Mutex()

  override fun listModels(): List<DuanYuModelDescriptor> {
    return modelRegistry.listModels()
  }

  override suspend fun chatCompletion(
    request: DuanYuChatCompletionRequest,
    onChunk: suspend (DuanYuChatCompletionChunk) -> Unit,
  ) {
    inferenceMutex.withLock {
      withContext(Dispatchers.Default) {
        val descriptor =
          modelRegistry.findModel(request.modelId)
            ?: throw IllegalArgumentException("Model '${request.modelId}' was not found.")
        if (descriptor.installState != DuanYuModelInstallState.INSTALLED) {
          throw IllegalStateException("Model '${request.modelId}' is not installed.")
        }
        val model =
          runtimeModelRegistry.findRuntimeModel(request.modelId)
            ?: throw IllegalStateException("Model '${request.modelId}' is not ready.")
        val preparedChat = prepareChat(request)

        ensureTextModelReady(
          model = model,
          systemInstruction = preparedChat.systemInstruction,
          initialMessages = preparedChat.initialMessages,
        )
        runTextChat(
          model = model,
          preparedChat = preparedChat,
          stream = request.stream,
          onChunk = onChunk,
        )
      }
    }
  }

  private suspend fun ensureTextModelReady(
    model: Model,
    systemInstruction: Contents?,
    initialMessages: List<Message>,
  ) {
    if (model.instance == null) {
      withTimeout(MODEL_INITIALIZATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
          model.runtimeHelper.initialize(
            context = context,
            model = model,
            taskId = BuiltInTaskId.LLM_CHAT,
            supportImage = false,
            supportAudio = false,
            systemInstruction = systemInstruction,
            coroutineScope = serviceScope,
            onDone = { message ->
              if (continuation.isActive) {
                if (model.instance != null) {
                  continuation.resume(Unit)
                } else if (message.isNotBlank()) {
                  continuation.resumeWithException(IllegalStateException(message))
                }
              }
            },
          )
        }
      }
    }

    model.runtimeHelper.resetConversation(
      model = model,
      supportImage = false,
      supportAudio = false,
      systemInstruction = systemInstruction,
      initialMessages = initialMessages,
    )
  }

  private suspend fun runTextChat(
    model: Model,
    preparedChat: PreparedChat,
    stream: Boolean,
    onChunk: suspend (DuanYuChatCompletionChunk) -> Unit,
  ) {
    val events = Channel<ChatEvent>(Channel.UNLIMITED)
    var completed = false

    try {
      model.runtimeHelper.runInference(
        model = model,
        input = preparedChat.input,
        resultListener = { partialResult, done, _ ->
          if (partialResult.startsWith("<ctrl")) {
            return@runInference
          }
          events.trySend(ChatEvent.Content(content = partialResult, done = done))
          if (done) {
            events.close()
          }
        },
        cleanUpListener = { events.close() },
        onError = { message ->
          events.trySend(ChatEvent.Error(IllegalStateException(message)))
          events.close()
        },
        coroutineScope = serviceScope,
      )

      val fullContent = StringBuilder()
      for (event in events) {
        when (event) {
          is ChatEvent.Content -> {
            if (stream) {
              if (event.content.isNotEmpty() || event.done) {
                onChunk(DuanYuChatCompletionChunk(content = event.content, done = event.done))
              }
            } else {
              fullContent.append(event.content)
              if (event.done) {
                onChunk(
                  DuanYuChatCompletionChunk(content = fullContent.toString(), done = true)
                )
              }
            }
            completed = event.done
          }
          is ChatEvent.Error -> throw event.throwable
        }
      }
    } finally {
      if (!completed) {
        model.runtimeHelper.stopResponse(model)
      }
      events.close()
    }
  }

  private fun prepareChat(request: DuanYuChatCompletionRequest): PreparedChat {
    if (request.messages.isEmpty()) {
      throw IllegalArgumentException("Messages must not be empty.")
    }

    val systemInstructionText =
      request.messages
        .filter { it.role.normalizedRole() in setOf(ROLE_SYSTEM, ROLE_DEVELOPER) }
        .joinToString(separator = "\n\n") { it.content.trim() }
        .trim()
    val conversationMessages =
      request.messages.filter { it.role.normalizedRole() !in setOf(ROLE_SYSTEM, ROLE_DEVELOPER) }
    val lastMessage =
      conversationMessages.lastOrNull()
        ?: throw IllegalArgumentException("A user message is required.")
    if (lastMessage.role.normalizedRole() != ROLE_USER) {
      throw IllegalArgumentException("The last chat message must use the user role.")
    }
    if (lastMessage.content.isBlank()) {
      throw IllegalArgumentException("The last user message must not be empty.")
    }

    val initialMessages =
      conversationMessages.dropLast(1).map { message ->
        when (message.role.normalizedRole()) {
          ROLE_USER -> Message.user(message.content)
          ROLE_ASSISTANT,
          ROLE_MODEL -> Message.model(message.content)
          else -> throw IllegalArgumentException("Unsupported chat role '${message.role}'.")
        }
      }

    return PreparedChat(
      input = lastMessage.content,
      initialMessages = initialMessages,
      systemInstruction =
        if (systemInstructionText.isNotEmpty()) Contents.of(systemInstructionText) else null,
    )
  }

  private fun String.normalizedRole(): String {
    return trim().lowercase()
  }

  private data class PreparedChat(
    val input: String,
    val initialMessages: List<Message>,
    val systemInstruction: Contents?,
  )

  private sealed class ChatEvent {
    data class Content(val content: String, val done: Boolean) : ChatEvent()

    data class Error(val throwable: Throwable) : ChatEvent()
  }

  private companion object {
    const val MODEL_INITIALIZATION_TIMEOUT_MS = 120_000L
    const val ROLE_ASSISTANT = "assistant"
    const val ROLE_DEVELOPER = "developer"
    const val ROLE_MODEL = "model"
    const val ROLE_SYSTEM = "system"
    const val ROLE_USER = "user"
  }
}
