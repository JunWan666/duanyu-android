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

package com.google.ai.edge.gallery.ui.home

import android.Manifest
import android.app.Activity
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mms
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.api.DuanYuApiSettingsViewModel
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.i18n.DuanYuLanguage
import com.google.ai.edge.gallery.i18n.DuanYuLocaleManager
import com.google.ai.edge.gallery.proto.Theme
import com.google.ai.edge.gallery.ui.common.TaskIcon
import com.google.ai.edge.gallery.ui.common.tos.AppTosDialog
import com.google.ai.edge.gallery.ui.common.tos.TosViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerUiState
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.ai.edge.gallery.ui.theme.customColors
import kotlinx.coroutines.delay

private const val NOTIFICATION_PERMISSION_DELAY_MS = 1200L
private val THEME_OPTIONS = listOf(Theme.THEME_AUTO, Theme.THEME_LIGHT, Theme.THEME_DARK)
private val LANGUAGE_OPTIONS =
  listOf(DuanYuLanguage.SYSTEM, DuanYuLanguage.ZH_CN, DuanYuLanguage.EN)

private enum class DuanYuSettingsDestination {
  ROOT,
  API,
  LANGUAGE,
  THEME,
  TERMS,
  ABOUT,
}

private data class DuanYuHomeTab(
  @param:StringRes val labelRes: Int,
  @param:StringRes val titleRes: Int,
  @param:StringRes val subtitleRes: Int,
  val taskId: String?,
  val icon: ImageVector,
)

private val HomeTabs =
  listOf(
    DuanYuHomeTab(
      labelRes = R.string.duanyu_tab_chat,
      titleRes = R.string.duanyu_feature_ai_chat,
      subtitleRes = R.string.duanyu_tab_chat_subtitle,
      taskId = BuiltInTaskId.LLM_CHAT,
      icon = Icons.Outlined.Forum,
    ),
    DuanYuHomeTab(
      labelRes = R.string.duanyu_tab_image,
      titleRes = R.string.duanyu_feature_ask_image,
      subtitleRes = R.string.duanyu_tab_image_subtitle,
      taskId = BuiltInTaskId.LLM_ASK_IMAGE,
      icon = Icons.Outlined.Mms,
    ),
    DuanYuHomeTab(
      labelRes = R.string.duanyu_tab_audio,
      titleRes = R.string.duanyu_feature_ask_audio,
      subtitleRes = R.string.duanyu_tab_audio_subtitle,
      taskId = BuiltInTaskId.LLM_ASK_AUDIO,
      icon = Icons.Outlined.Mic,
    ),
    DuanYuHomeTab(
      labelRes = R.string.duanyu_tab_skills,
      titleRes = R.string.duanyu_feature_agent_skills,
      subtitleRes = R.string.duanyu_tab_skills_subtitle,
      taskId = BuiltInTaskId.LLM_AGENT_CHAT,
      icon = Icons.Outlined.AutoAwesome,
    ),
    DuanYuHomeTab(
      labelRes = R.string.duanyu_tab_settings,
      titleRes = R.string.duanyu_settings_title,
      subtitleRes = R.string.duanyu_tab_settings_subtitle,
      taskId = null,
      icon = Icons.Outlined.Settings,
    ),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuanYuHomeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  tosViewModel: TosViewModel,
  navigateToTaskScreen: (Task) -> Unit,
  onModelsClicked: () -> Unit,
  onNotificationsClicked: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current
  var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
  var showTosDialog by remember { mutableStateOf(!tosViewModel.getIsTosAccepted()) }
  var loadingModelAllowlistDelayed by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.loadingModelAllowlist) {
    if (uiState.loadingModelAllowlist) {
      delay(200)
      loadingModelAllowlistDelayed = uiState.loadingModelAllowlist
    } else {
      loadingModelAllowlistDelayed = false
    }
  }

  val requestPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  LaunchedEffect(showTosDialog) {
    if (!showTosDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      delay(NOTIFICATION_PERMISSION_DELAY_MS)
      val permissionState =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
      if (permissionState != PackageManager.PERMISSION_GRANTED) {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  if (!showTosDialog) {
    Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      topBar = {
        CenterAlignedTopAppBar(
          title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = stringResource(R.string.app_name_second_part),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
        )
      },
      bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
          HomeTabs.forEachIndexed { index, tab ->
            NavigationBarItem(
              selected = selectedTabIndex == index,
              onClick = { selectedTabIndex = index },
              icon = { Icon(tab.icon, contentDescription = null) },
              label = { Text(stringResource(tab.labelRes), maxLines = 1) },
              alwaysShowLabel = true,
            )
          }
        }
      },
    ) { innerPadding ->
      Box(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
      ) {
        when {
          loadingModelAllowlistDelayed -> {
            DuanYuLoadingState(modifier = Modifier.align(Alignment.Center))
          }
          uiState.loadingModelAllowlist -> {
            Spacer(modifier = Modifier.fillMaxSize())
          }
          selectedTabIndex == HomeTabs.lastIndex -> {
            DuanYuSettingsPage(
              currentTheme = modelManagerViewModel.readThemeOverride(),
              modelManagerViewModel = modelManagerViewModel,
              onModelsClicked = onModelsClicked,
              onNotificationsClicked = onNotificationsClicked,
            )
          }
          else -> {
            val tab = HomeTabs[selectedTabIndex]
            val task = tab.taskId?.let { modelManagerViewModel.getTaskById(it) }
            DuanYuTaskPage(
              tab = tab,
              task = task,
              uiState = uiState,
              onStart = navigateToTaskScreen,
            )
          }
        }
      }
    }
  }

  if (showTosDialog) {
    AppTosDialog(
      onTosAccepted = {
        showTosDialog = false
        tosViewModel.acceptTos()
      }
    )
  }

  if (uiState.loadingModelAllowlistError.isNotEmpty()) {
    AlertDialog(
      icon = {
        Icon(
          Icons.Rounded.Error,
          contentDescription = stringResource(R.string.cd_error),
          tint = MaterialTheme.colorScheme.error,
        )
      },
      title = { Text(stringResource(R.string.duanyu_model_list_load_failed)) },
      text = { Text(uiState.loadingModelAllowlistError) },
      onDismissRequest = { modelManagerViewModel.clearLoadModelAllowlistError() },
      confirmButton = {
        TextButton(onClick = { modelManagerViewModel.loadModelAllowlist() }) {
          Text(stringResource(R.string.duanyu_action_retry))
        }
      },
      dismissButton = {
        TextButton(onClick = { modelManagerViewModel.clearLoadModelAllowlistError() }) {
          Text(stringResource(R.string.duanyu_action_close))
        }
      },
    )
  }
}

@Composable
private fun DuanYuTaskPage(
  tab: DuanYuHomeTab,
  task: Task?,
  uiState: ModelManagerUiState,
  onStart: (Task) -> Unit,
) {
  val tabLabel = stringResource(tab.labelRes)
  val tabTitle = stringResource(tab.titleRes)
  val tabSubtitle = stringResource(tab.subtitleRes)
  val context = LocalContext.current
  val colors = MaterialTheme.customColors.taskBgGradientColors
  val gradientColors =
    remember(tab.taskId, colors) {
      val index = HomeTabs.indexOf(tab).coerceAtLeast(0) % colors.size.coerceAtLeast(1)
      if (colors.isEmpty()) {
        listOf(Color(0xFF1F7A55), Color(0xFF2456A6))
      } else {
        colors[index]
      }
    }
  val models = task?.models.orEmpty()
  val downloadedCount =
    models.count { model ->
      uiState.modelDownloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
    }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Surface(
              modifier = Modifier.size(56.dp),
              shape = RoundedCornerShape(8.dp),
              color = Color.Transparent,
            ) {
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .background(Brush.linearGradient(gradientColors), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(tab.icon, contentDescription = null, tint = Color.White)
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = tabLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = tabTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DuanYuStatPill(
            label = stringResource(R.string.duanyu_models_label),
            value = models.size.toString(),
            modifier = Modifier.weight(1f),
          )
          DuanYuStatPill(
            label = stringResource(R.string.duanyu_downloaded_label),
            value = downloadedCount.toString(),
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Button(
          enabled = task != null,
          onClick = { task?.let(onStart) },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(8.dp),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null)
          Text(
            text = stringResource(R.string.duanyu_select_model_start),
            modifier = Modifier.padding(start = 8.dp),
          )
        }
      }
    }

    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(
          text = stringResource(R.string.duanyu_current_entry),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (task != null) {
            TaskIcon(task = task, width = 48.dp)
          } else {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
              Icon(tab.icon, contentDescription = null, modifier = Modifier.padding(12.dp))
            }
          }
          Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
              text = task?.displayLabel(context) ?: tabTitle,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = tabSubtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DuanYuSettingsPage(
  currentTheme: Theme,
  modelManagerViewModel: ModelManagerViewModel,
  onModelsClicked: () -> Unit,
  onNotificationsClicked: () -> Unit,
) {
  val context = LocalContext.current
  var destination by rememberSaveable { mutableStateOf(DuanYuSettingsDestination.ROOT) }
  var selectedTheme by remember(currentTheme) { mutableStateOf(currentTheme) }
  var selectedLanguage by remember { mutableStateOf(DuanYuLocaleManager.readLanguage(context)) }

  BackHandler(enabled = destination != DuanYuSettingsDestination.ROOT) {
    destination = DuanYuSettingsDestination.ROOT
  }

  when (destination) {
    DuanYuSettingsDestination.ROOT ->
      DuanYuSettingsRootPage(
        onModelsClicked = onModelsClicked,
        onNotificationsClicked = onNotificationsClicked,
        onApiClicked = { destination = DuanYuSettingsDestination.API },
        onLanguageClicked = { destination = DuanYuSettingsDestination.LANGUAGE },
        onThemeClicked = { destination = DuanYuSettingsDestination.THEME },
        onTermsClicked = { destination = DuanYuSettingsDestination.TERMS },
        onAboutClicked = { destination = DuanYuSettingsDestination.ABOUT },
      )

    DuanYuSettingsDestination.API ->
      DuanYuApiSettingsPage(onBack = { destination = DuanYuSettingsDestination.ROOT })

    DuanYuSettingsDestination.LANGUAGE ->
      DuanYuSettingsDetailPage(
        title = stringResource(R.string.duanyu_language_settings_title),
        subtitle = stringResource(R.string.duanyu_language_settings_subtitle),
        icon = Icons.Outlined.Language,
        onBack = { destination = DuanYuSettingsDestination.ROOT },
      ) {
        MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          LANGUAGE_OPTIONS.forEachIndexed { index, language ->
            SegmentedButton(
              shape =
                SegmentedButtonDefaults.itemShape(index = index, count = LANGUAGE_OPTIONS.size),
              onCheckedChange = {
                if (selectedLanguage != language) {
                  selectedLanguage = language
                  DuanYuLocaleManager.saveLanguage(context, language)
                  context.findActivity()?.recreate()
                }
              },
              checked = language == selectedLanguage,
              label = { Text(languageLabel(language)) },
            )
          }
        }
      }

    DuanYuSettingsDestination.THEME ->
      DuanYuSettingsDetailPage(
        title = stringResource(R.string.duanyu_theme_settings_title),
        subtitle = stringResource(R.string.duanyu_theme_settings_subtitle),
        icon = Icons.Outlined.Palette,
        onBack = { destination = DuanYuSettingsDestination.ROOT },
      ) {
        MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          THEME_OPTIONS.forEachIndexed { index, theme ->
            SegmentedButton(
              shape = SegmentedButtonDefaults.itemShape(index = index, count = THEME_OPTIONS.size),
              onCheckedChange = {
                selectedTheme = theme
                ThemeSettings.themeOverride.value = theme
                modelManagerViewModel.saveThemeOverride(theme)
                context.updateNightMode(theme)
              },
              checked = theme == selectedTheme,
              label = { Text(themeLabel(theme)) },
            )
          }
        }
      }

    DuanYuSettingsDestination.TERMS ->
      DuanYuSettingsDetailPage(
        title = stringResource(R.string.duanyu_terms_title),
        subtitle = stringResource(R.string.duanyu_terms_subtitle),
        icon = Icons.Outlined.Security,
        onBack = { destination = DuanYuSettingsDestination.ROOT },
      ) {
        Text(
          stringResource(R.string.tos_dialog_content_app),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

    DuanYuSettingsDestination.ABOUT ->
      DuanYuSettingsDetailPage(
        title = stringResource(R.string.duanyu_about_title),
        subtitle =
          stringResource(
            R.string.duanyu_about_subtitle,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
          ),
        icon = Icons.Outlined.Info,
        onBack = { destination = DuanYuSettingsDestination.ROOT },
      ) {
        DuanYuSettingsRow(
          icon = Icons.Rounded.Verified,
          title = stringResource(R.string.duanyu_about_version_title),
          subtitle =
            stringResource(
              R.string.duanyu_about_subtitle,
              BuildConfig.VERSION_NAME,
              BuildConfig.VERSION_CODE,
            ),
          onClick = {},
        )
        HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
        DuanYuSettingsRow(
          icon = Icons.AutoMirrored.Rounded.ListAlt,
          title = stringResource(R.string.duanyu_licenses_title),
          subtitle = stringResource(R.string.duanyu_licenses_subtitle),
          onClick = { context.startActivity(Intent(context, OssLicensesMenuActivity::class.java)) },
        )
      }
  }
}

@Composable
private fun DuanYuSettingsRootPage(
  onModelsClicked: () -> Unit,
  onNotificationsClicked: () -> Unit,
  onApiClicked: () -> Unit,
  onLanguageClicked: () -> Unit,
  onThemeClicked: () -> Unit,
  onTermsClicked: () -> Unit,
  onAboutClicked: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
          stringResource(R.string.duanyu_settings_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          stringResource(R.string.duanyu_settings_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DuanYuSettingsTile(
          icon = Icons.AutoMirrored.Rounded.ListAlt,
          title = stringResource(R.string.duanyu_model_manager_title),
          subtitle = stringResource(R.string.duanyu_model_manager_subtitle),
          modifier = Modifier.weight(1f),
          onClick = onModelsClicked,
        )
        DuanYuSettingsTile(
          icon = Icons.Outlined.Api,
          title = stringResource(R.string.duanyu_api_service_title),
          subtitle = stringResource(R.string.duanyu_api_service_subtitle),
          trailingText = stringResource(R.string.duanyu_status_available),
          modifier = Modifier.weight(1f),
          onClick = onApiClicked,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DuanYuSettingsTile(
          icon = Icons.Rounded.Notifications,
          title = stringResource(R.string.duanyu_notifications_title),
          subtitle = stringResource(R.string.duanyu_notifications_subtitle),
          modifier = Modifier.weight(1f),
          onClick = onNotificationsClicked,
        )
        DuanYuSettingsTile(
          icon = Icons.Outlined.Language,
          title = stringResource(R.string.duanyu_language_settings_title),
          subtitle = stringResource(R.string.duanyu_language_settings_subtitle),
          modifier = Modifier.weight(1f),
          onClick = onLanguageClicked,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DuanYuSettingsTile(
          icon = Icons.Outlined.Palette,
          title = stringResource(R.string.duanyu_theme_settings_title),
          subtitle = stringResource(R.string.duanyu_theme_settings_subtitle),
          modifier = Modifier.weight(1f),
          onClick = onThemeClicked,
        )
        DuanYuSettingsTile(
          icon = Icons.Outlined.Security,
          title = stringResource(R.string.duanyu_terms_title),
          subtitle = stringResource(R.string.duanyu_terms_subtitle),
          modifier = Modifier.weight(1f),
          onClick = onTermsClicked,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DuanYuSettingsTile(
          icon = Icons.Outlined.Info,
          title = stringResource(R.string.duanyu_about_title),
          subtitle = stringResource(R.string.duanyu_about_subtitle_short),
          modifier = Modifier.weight(1f),
          onClick = onAboutClicked,
        )
        Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun DuanYuApiSettingsPage(
  onBack: () -> Unit,
  viewModel: DuanYuApiSettingsViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsState()
  val tokenCopiedMessage = stringResource(R.string.duanyu_api_token_copied)
  val baseUrlCopiedMessage = stringResource(R.string.duanyu_api_base_url_copied)
  val curlCopiedMessage = stringResource(R.string.duanyu_api_curl_copied)
  val tokenRefreshedMessage = stringResource(R.string.duanyu_api_token_refreshed)
  val curlExample = remember(state.baseUrl, state.apiToken) {
    buildDuanYuApiCurlExample(baseUrl = state.baseUrl, token = state.apiToken)
  }

  DuanYuSettingsDetailPage(
    title = stringResource(R.string.duanyu_api_service_title),
    subtitle = stringResource(R.string.duanyu_api_service_subtitle),
    icon = Icons.Outlined.Api,
    onBack = onBack,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(
        stringResource(R.string.duanyu_api_service_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider()
      DuanYuSettingsRow(
        icon = Icons.Outlined.Api,
        title = stringResource(R.string.duanyu_api_service_switch_title),
        subtitle =
          if (state.running) {
            stringResource(R.string.duanyu_api_service_running)
          } else {
            stringResource(R.string.duanyu_api_service_stopped)
          },
        trailingText = null,
        onClick = {
          if (state.running) {
            viewModel.stopService()
          } else {
            viewModel.startService()
          }
        },
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            stringResource(R.string.duanyu_api_service_enable),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
          )
          Text(
            stringResource(R.string.duanyu_api_service_enable_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = state.running,
          onCheckedChange = { checked ->
            if (checked) {
              viewModel.startService()
            } else {
              viewModel.stopService()
            }
          },
        )
      }
      HorizontalDivider()
      DuanYuSettingsRow(
        icon = Icons.Rounded.Verified,
        title = stringResource(R.string.duanyu_api_base_url_title),
        subtitle = state.baseUrl,
        trailingIcon = Icons.Rounded.ContentCopy,
        onClick = {
          copyTextToClipboard(
            context = context,
            label = "duanyu_api_base_url",
            text = state.baseUrl,
            message = baseUrlCopiedMessage,
          )
        },
      )
      DuanYuSettingsRow(
        icon = Icons.Outlined.Security,
        title = stringResource(R.string.duanyu_api_token_title),
        subtitle = state.apiToken.maskApiToken(),
        trailingIcon = Icons.Rounded.ContentCopy,
        onClick = {
          copyTextToClipboard(
            context = context,
            label = "duanyu_api_token",
            text = state.apiToken,
            message = tokenCopiedMessage,
          )
        },
      )
      DuanYuSettingsRow(
        icon = Icons.Rounded.Refresh,
        title = stringResource(R.string.duanyu_api_regenerate_token_title),
        subtitle = stringResource(R.string.duanyu_api_regenerate_token_subtitle),
        onClick = {
          viewModel.regenerateApiToken()
          Toast.makeText(context, tokenRefreshedMessage, Toast.LENGTH_SHORT).show()
        },
      )
      DuanYuSettingsRow(
        icon = Icons.Outlined.Terminal,
        title = stringResource(R.string.duanyu_api_curl_title),
        subtitle = stringResource(R.string.duanyu_api_curl_subtitle),
        trailingIcon = Icons.Rounded.ContentCopy,
        onClick = {
          copyTextToClipboard(
            context = context,
            label = "duanyu_api_curl",
            text = curlExample,
            message = curlCopiedMessage,
          )
        },
      )
      DuanYuSettingsRow(
        icon = Icons.Outlined.Security,
        title = stringResource(R.string.duanyu_api_listen_scope_title),
        subtitle = stringResource(R.string.duanyu_api_listen_scope_localhost),
        enabled = false,
        onClick = {},
      )
      if (state.errorMessage != null) {
        Text(
          text = state.errorMessage.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(horizontal = 18.dp),
        )
      }
    }
  }
}

@Composable
private fun DuanYuSettingsTile(
  icon: ImageVector,
  title: String,
  subtitle: String,
  trailingText: String? = null,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    modifier = modifier.height(104.dp),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().clickable(onClick = onClick).padding(12.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(7.dp).size(18.dp),
          )
        }
        if (trailingText != null) {
          Text(
            trailingText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        } else {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column {
        Text(
          title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun DuanYuSettingsDetailPage(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onBack: () -> Unit,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.clickable(onClick = onBack),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_navigate_back_icon),
              modifier = Modifier.padding(10.dp).size(22.dp),
            )
          }
          Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(10.dp).size(22.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
              subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
  }
}

@Composable
private fun DuanYuSettingsRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  enabled: Boolean = true,
  trailingText: String? = null,
  trailingIcon: ImageVector? = null,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(enabled = enabled, onClick = onClick)
        .padding(18.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color =
        if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint =
          if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(10.dp).size(22.dp),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (trailingText != null) {
      Text(
        trailingText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else if (trailingIcon != null) {
      IconButton(onClick = onClick, enabled = enabled) {
        Icon(
          imageVector = trailingIcon,
          contentDescription = null,
          tint =
            if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else if (enabled) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun DuanYuStatPill(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
  }
}

private fun String.maskApiToken(): String {
  if (isBlank()) {
    return ""
  }
  if (length <= 12) {
    return this
  }
  return "${take(8)}...${takeLast(4)}"
}

private fun copyTextToClipboard(context: Context, label: String, text: String, message: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
  Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun buildDuanYuApiCurlExample(baseUrl: String, token: String): String {
  return """
    curl ${baseUrl.trimEnd('/')}/v1/chat/completions \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -d '{"model":"<model-id>","messages":[{"role":"user","content":"Hello from DuanYu."}],"stream":false}'
  """
    .trimIndent()
}

@Composable
private fun DuanYuLoadingState(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 18.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
    Text(stringResource(R.string.duanyu_loading_model_list), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun themeLabel(theme: Theme): String {
  return when (theme) {
    Theme.THEME_AUTO -> stringResource(R.string.duanyu_theme_auto)
    Theme.THEME_LIGHT -> stringResource(R.string.duanyu_theme_light)
    Theme.THEME_DARK -> stringResource(R.string.duanyu_theme_dark)
    else -> stringResource(R.string.duanyu_unknown)
  }
}

@Composable
private fun languageLabel(language: DuanYuLanguage): String {
  return when (language) {
    DuanYuLanguage.SYSTEM -> stringResource(R.string.duanyu_language_system)
    DuanYuLanguage.ZH_CN -> stringResource(R.string.duanyu_language_zh_cn)
    DuanYuLanguage.EN -> stringResource(R.string.duanyu_language_en)
  }
}

private fun Context.updateNightMode(theme: Theme) {
  val uiModeManager = applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
  when (theme) {
    Theme.THEME_AUTO -> uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
    Theme.THEME_LIGHT -> uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
    Theme.THEME_DARK -> uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
    else -> uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
  }
}

private fun Context.findActivity(): Activity? {
  var context = this
  while (context is ContextWrapper) {
    if (context is Activity) {
      return context
    }
    context = context.baseContext
  }
  return null
}
