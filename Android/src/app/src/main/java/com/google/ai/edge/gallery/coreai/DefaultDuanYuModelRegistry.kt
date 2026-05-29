/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.coreai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DefaultDuanYuModelRegistry
@Inject
constructor() : DuanYuModelRegistry {
  private val _models = MutableStateFlow<List<DuanYuModelDescriptor>>(emptyList())
  val models: StateFlow<List<DuanYuModelDescriptor>> = _models.asStateFlow()

  override fun updateModels(models: List<DuanYuModelDescriptor>) {
    _models.value = models.sortedBy { it.displayName.ifEmpty { it.id } }
  }

  override fun listModels(): List<DuanYuModelDescriptor> {
    return _models.value
  }

  override fun findModel(modelId: String): DuanYuModelDescriptor? {
    return _models.value.firstOrNull { it.id == modelId }
  }
}
