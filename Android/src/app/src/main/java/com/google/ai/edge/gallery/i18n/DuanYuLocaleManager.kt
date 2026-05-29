/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

enum class DuanYuLanguage(val storageValue: String, val languageTag: String?) {
  SYSTEM("system", null),
  ZH_CN("zh-CN", "zh-CN"),
  EN("en", "en"),
}

object DuanYuLocaleManager {
  private const val PREFS_NAME = "duanyu_locale"
  private const val KEY_LANGUAGE = "language"

  fun readLanguage(context: Context): DuanYuLanguage {
    val value =
      context
        .applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, DuanYuLanguage.SYSTEM.storageValue)
    return DuanYuLanguage.entries.firstOrNull { it.storageValue == value } ?: DuanYuLanguage.SYSTEM
  }

  fun saveLanguage(context: Context, language: DuanYuLanguage) {
    context
      .applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_LANGUAGE, language.storageValue)
      .apply()
  }

  fun wrapContext(base: Context): Context {
    val language = readLanguage(base)
    val tag = language.languageTag
    if (tag == null) {
      val systemLocale = base.resources.configuration.locales[0] ?: Locale.getDefault()
      Locale.setDefault(systemLocale)
      return base
    }
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)

    val configuration = Configuration(base.resources.configuration)
    configuration.setLocales(LocaleList(locale))
    return base.createConfigurationContext(configuration)
  }
}
