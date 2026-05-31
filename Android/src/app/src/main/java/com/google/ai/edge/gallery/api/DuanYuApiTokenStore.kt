/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.api

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuanYuApiTokenStore
@Inject
constructor(@ApplicationContext private val context: Context) {
  private val secureRandom = SecureRandom()
  private val prefs by lazy {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  }

  fun getOrCreateToken(): String {
    val existingToken = prefs.getString(KEY_API_TOKEN, null)
    if (!existingToken.isNullOrBlank()) {
      return existingToken
    }
    return regenerateToken()
  }

  fun regenerateToken(): String {
    val bytes = ByteArray(TOKEN_BYTE_COUNT)
    secureRandom.nextBytes(bytes)
    val token =
      TOKEN_PREFIX +
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    prefs.edit().putString(KEY_API_TOKEN, token).commit()
    return token
  }

  private companion object {
    const val PREFERENCES_NAME = "duanyu_api"
    const val KEY_API_TOKEN = "api_token"
    const val TOKEN_PREFIX = "dy-"
    const val TOKEN_BYTE_COUNT = 32
  }
}
