/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.api

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class DuanYuApiSettingsViewModel
@Inject
constructor(private val controller: DuanYuApiServiceController) : ViewModel() {
  val state: StateFlow<DuanYuApiServiceState> = controller.state

  fun startService() {
    controller.start()
  }

  fun stopService() {
    controller.stop()
  }
}
