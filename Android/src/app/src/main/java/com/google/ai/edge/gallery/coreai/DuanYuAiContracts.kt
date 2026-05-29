/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.coreai

enum class DuanYuAiTaskType {
  CHAT,
  IMAGE,
  AUDIO,
  AGENT,
}

enum class DuanYuModelInstallState {
  NOT_INSTALLED,
  INSTALLED,
  IN_PROGRESS,
  FAILED,
  UNKNOWN,
}

data class DuanYuModelDescriptor(
  val id: String,
  val displayName: String,
  val taskTypes: Set<DuanYuAiTaskType>,
  val installState: DuanYuModelInstallState,
  val imported: Boolean,
  val runtimeType: String,
  val fileName: String,
  val sizeInBytes: Long,
  val path: String?,
  val supportsImage: Boolean,
  val supportsAudio: Boolean,
)

data class DuanYuChatMessage(
  val role: String,
  val content: String,
)

data class DuanYuChatCompletionRequest(
  val modelId: String,
  val messages: List<DuanYuChatMessage>,
  val stream: Boolean = false,
)

data class DuanYuChatCompletionChunk(
  val content: String,
  val done: Boolean,
)

interface DuanYuModelRegistry {
  fun updateModels(models: List<DuanYuModelDescriptor>)

  fun listModels(): List<DuanYuModelDescriptor>

  fun findModel(modelId: String): DuanYuModelDescriptor?
}

interface DuanYuAiService {
  fun listModels(): List<DuanYuModelDescriptor>

  suspend fun chatCompletion(
    request: DuanYuChatCompletionRequest,
    onChunk: suspend (DuanYuChatCompletionChunk) -> Unit,
  )
}
