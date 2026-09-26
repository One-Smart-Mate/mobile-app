package com.ih.osm.features.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class AppPermissionKind {
    NOTIFICATIONS,
    CAMERA,
    MICROPHONE,
    GALLERY,
    BACKGROUND_TASKS,
}

enum class AppPermissionStatus {
    GRANTED,
    PARTIAL,
    MISSING,
    SYSTEM_MANAGED,
}

data class AppPermissionItem(
    val kind: AppPermissionKind,
    val status: AppPermissionStatus,
)

data class AppPermissionSnapshot(
    val items: List<AppPermissionItem>,
    val missingRuntimePermissions: List<String>,
) {
    val allRuntimePermissionsGranted: Boolean
        get() = missingRuntimePermissions.isEmpty()
}

class PermissionHelper(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): AppPermissionSnapshot {
        val notificationPermissions = notificationPermissions()
        val cameraPermissions = listOf(Manifest.permission.CAMERA)
        val microphonePermissions = listOf(Manifest.permission.RECORD_AUDIO)
        val galleryPermissions = galleryReadPermissions()
        val galleryRequestPermissions = galleryRequestPermissions()

        val notificationStatus = statusFor(notificationPermissions)
        val cameraStatus = statusFor(cameraPermissions)
        val microphoneStatus = statusFor(microphonePermissions)
        val galleryStatus = galleryStatus(galleryPermissions)
        val missing = buildList {
            addAll(notificationPermissions.filterNot(::isGranted))
            addAll(cameraPermissions.filterNot(::isGranted))
            addAll(microphonePermissions.filterNot(::isGranted))
            if (galleryStatus == AppPermissionStatus.MISSING) {
                addAll(galleryRequestPermissions.filterNot(::isGranted))
            }
        }.distinct()

        return AppPermissionSnapshot(
            items = listOf(
                AppPermissionItem(AppPermissionKind.NOTIFICATIONS, notificationStatus),
                AppPermissionItem(AppPermissionKind.CAMERA, cameraStatus),
                AppPermissionItem(AppPermissionKind.MICROPHONE, microphoneStatus),
                AppPermissionItem(AppPermissionKind.GALLERY, galleryStatus),
                AppPermissionItem(AppPermissionKind.BACKGROUND_TASKS, AppPermissionStatus.SYSTEM_MANAGED),
            ),
            missingRuntimePermissions = missing,
        )
    }

    fun appSettingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${appContext.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun notificationPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    private fun galleryReadPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun galleryRequestPermissions(): List<String> = buildList {
        addAll(galleryReadPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
    }

    private fun galleryStatus(permissions: List<String>): AppPermissionStatus {
        if (permissions.isEmpty()) return AppPermissionStatus.GRANTED
        val grantedCount = permissions.count(::isGranted)
        val hasSelectedMediaAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return when {
            grantedCount == permissions.size -> AppPermissionStatus.GRANTED
            grantedCount > 0 || hasSelectedMediaAccess -> AppPermissionStatus.PARTIAL
            else -> AppPermissionStatus.MISSING
        }
    }

    private fun statusFor(permissions: List<String>): AppPermissionStatus =
        if (permissions.all(::isGranted)) AppPermissionStatus.GRANTED else AppPermissionStatus.MISSING

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
}
