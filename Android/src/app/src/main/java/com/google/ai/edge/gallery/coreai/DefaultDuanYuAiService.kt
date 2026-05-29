/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.coreai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDuanYuAiService
@Inject
constructor(private val modelRegistry: DuanYuModelRegistry) : DuanYuAiService {
  override fun listModels(): List<DuanYuModelDescriptor> {
    return modelRegistry.listModels()
  }

  override suspend fun chatCompletion(
    request: DuanYuChatCompletionRequest,
    onChunk: suspend (DuanYuChatCompletionChunk) -> Unit,
  ) {
    // The inference bridge will be wired after the model lifecycle is separated from UI ViewModels.
    throw UnsupportedOperationException("Chat completion is not wired yet.")
  }
}
