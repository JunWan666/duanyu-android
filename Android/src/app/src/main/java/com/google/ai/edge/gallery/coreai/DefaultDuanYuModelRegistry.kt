/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.coreai

import com.google.ai.edge.gallery.data.Model
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DefaultDuanYuModelRegistry
@Inject
constructor() : DuanYuModelRegistry, DuanYuRuntimeModelRegistry {
  private val _models = MutableStateFlow<List<DuanYuModelDescriptor>>(emptyList())
  private val runtimeModels = mutableMapOf<String, Model>()
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

  override fun updateRuntimeModels(models: List<Model>) {
    synchronized(runtimeModels) {
      runtimeModels.clear()
      for (model in models) {
        runtimeModels[model.name] = model
      }
    }
  }

  override fun findRuntimeModel(modelId: String): Model? {
    return synchronized(runtimeModels) { runtimeModels[modelId] }
  }
}
