/*
 * Copyright 2026 DuanYu contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.google.ai.edge.gallery.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.coreai.DuanYuAiService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class DuanYuApiForegroundService : Service() {
  @Inject lateinit var controller: DuanYuApiServiceController
  @Inject lateinit var aiService: DuanYuAiService

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var httpServer: DuanYuLocalHttpServer? = null

  override fun onCreate() {
    super.onCreate()
    ensureNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> stopApiService()
      else -> startApiService()
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    stopApiService(stopSelf = false)
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun startApiService() {
    startForegroundWithNotification()
    if (httpServer != null) {
      controller.markStarted(DUANYU_API_DEFAULT_HOST, DUANYU_API_DEFAULT_PORT)
      return
    }

    val server =
      DuanYuLocalHttpServer(
        host = DUANYU_API_DEFAULT_HOST,
        port = DUANYU_API_DEFAULT_PORT,
        aiService = aiService,
        apiTokenProvider = controller::apiToken,
        scope = serviceScope,
      )
    httpServer = server
    server.start(
      onStarted = {
        controller.markStarted(DUANYU_API_DEFAULT_HOST, DUANYU_API_DEFAULT_PORT)
        updateNotification()
      },
      onError = { message ->
        controller.markError(message)
        stopApiService()
      },
    )
  }

  private fun stopApiService(stopSelf: Boolean = true) {
    httpServer?.stop()
    httpServer = null
    controller.markStopped()
    stopForeground(STOP_FOREGROUND_REMOVE)
    if (stopSelf) {
      stopSelf()
    }
  }

  private fun startForegroundWithNotification() {
    val notification = buildNotification()
    startForeground(
      NOTIFICATION_ID,
      notification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
  }

  private fun updateNotification() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun buildNotification() =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.duanyu_api_notification_title))
      .setContentText(
        getString(
          R.string.duanyu_api_notification_content,
          DUANYU_API_DEFAULT_HOST,
          DUANYU_API_DEFAULT_PORT,
        )
      )
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setOngoing(true)
      .setContentIntent(buildContentIntent())
      .build()

  private fun buildContentIntent(): PendingIntent {
    val intent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    return PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  private fun ensureNotificationChannel() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.duanyu_api_notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      )
    manager.createNotificationChannel(channel)
  }

  companion object {
    const val ACTION_START = "com.google.ai.edge.gallery.api.START"
    const val ACTION_STOP = "com.google.ai.edge.gallery.api.STOP"

    private const val CHANNEL_ID = "duanyu_api_service"
    private const val NOTIFICATION_ID = 20210531
  }
}
