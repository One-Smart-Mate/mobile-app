package com.ih.osm.features.permissions.data.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ih.osm.features.permissions.domain.manager.PermissionManager
import com.ih.osm.features.permissions.domain.model.AppPermissionItem
import com.ih.osm.features.permissions.domain.model.AppPermissionKind
import com.ih.osm.features.permissions.domain.model.AppPermissionSnapshot
import com.ih.osm.features.permissions.domain.model.AppPermissionStatus

class AndroidPermissionManager(context: Context) : PermissionManager {
    private val appContext = context.applicationContext

    override fun snapshot(): AppPermissionSnapshot {
        val notificationPermissions = notificationPermissions()
        val cameraPermissions = listOf(Manifest.permission.CAMERA)
        val microphonePermissions = listOf(Manifest.permission.RECORD_AUDIO)

        val notificationStatus = statusFor(notificationPermissions)
        val cameraStatus = statusFor(cameraPermissions)
        val microphoneStatus = statusFor(microphonePermissions)
        val missing = buildList {
            addAll(notificationPermissions.filterNot(::isGranted))
            addAll(cameraPermissions.filterNot(::isGranted))
            addAll(microphonePermissions.filterNot(::isGranted))
        }.distinct()

        return AppPermissionSnapshot(
            items = listOf(
                AppPermissionItem(AppPermissionKind.NOTIFICATIONS, notificationStatus),
                AppPermissionItem(AppPermissionKind.CAMERA, cameraStatus),
                AppPermissionItem(AppPermissionKind.MICROPHONE, microphoneStatus),
                AppPermissionItem(AppPermissionKind.BACKGROUND_TASKS, AppPermissionStatus.SYSTEM_MANAGED),
            ),
            missingRuntimePermissions = missing,
        )
    }

    private fun notificationPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    private fun statusFor(permissions: List<String>): AppPermissionStatus =
        if (permissions.all(::isGranted)) AppPermissionStatus.GRANTED else AppPermissionStatus.MISSING

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
}
