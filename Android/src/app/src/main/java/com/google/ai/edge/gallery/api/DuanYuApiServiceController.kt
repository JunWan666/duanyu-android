/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.api

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DuanYuApiServiceController
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val tokenStore: DuanYuApiTokenStore,
) {
  private val _state = MutableStateFlow(DuanYuApiServiceState(apiToken = tokenStore.getOrCreateToken()))
  val state: StateFlow<DuanYuApiServiceState> = _state.asStateFlow()

  fun apiToken(): String = tokenStore.getOrCreateToken()

  fun regenerateApiToken(): String {
    val token = tokenStore.regenerateToken()
    _state.value = _state.value.copy(apiToken = token)
    return token
  }

  fun start() {
    val intent =
      Intent(context, DuanYuApiForegroundService::class.java).apply {
        action = DuanYuApiForegroundService.ACTION_START
      }
    ContextCompat.startForegroundService(context, intent)
  }

  fun stop() {
    context.stopService(Intent(context, DuanYuApiForegroundService::class.java))
    _state.value = DuanYuApiServiceState(apiToken = apiToken())
  }

  internal fun markStarted(host: String, port: Int) {
    _state.value =
      DuanYuApiServiceState(
        running = true,
        host = host,
        port = port,
        apiToken = apiToken(),
        startedAtMillis = System.currentTimeMillis(),
      )
  }

  internal fun markStopped() {
    _state.value = DuanYuApiServiceState(apiToken = apiToken())
  }

  internal fun markError(message: String) {
    _state.value = _state.value.copy(running = false, apiToken = apiToken(), errorMessage = message)
  }
}
