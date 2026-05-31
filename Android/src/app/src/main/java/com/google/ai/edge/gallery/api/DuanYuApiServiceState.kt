/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.api

const val DUANYU_API_DEFAULT_HOST = "127.0.0.1"
const val DUANYU_API_DEFAULT_PORT = 8765

data class DuanYuApiServiceState(
  val running: Boolean = false,
  val host: String = DUANYU_API_DEFAULT_HOST,
  val port: Int = DUANYU_API_DEFAULT_PORT,
  val apiToken: String = "",
  val startedAtMillis: Long? = null,
  val errorMessage: String? = null,
) {
  val baseUrl: String
    get() = "http://$host:$port"
}
