package com.ih.osm.features.permissions.domain.manager

import com.ih.osm.features.permissions.domain.model.AppPermissionSnapshot

interface PermissionManager {
    fun snapshot(): AppPermissionSnapshot
}
