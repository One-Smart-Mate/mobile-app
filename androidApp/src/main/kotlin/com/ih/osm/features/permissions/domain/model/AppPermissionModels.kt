package com.ih.osm.features.permissions.domain.model

enum class AppPermissionKind {
    NOTIFICATIONS,
    CAMERA,
    MICROPHONE,
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
