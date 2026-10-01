package com.ih.osm.features.opl

import com.ih.osm.features.level.domain.model.Level

data class OplLevelChoice(
    val id: String,
    val title: String,
    val subtitle: String?,
    val hasChildren: Boolean,
)

/** Mirrors Create Card's local hierarchy, active filter and machine/owner search. */
internal class LocalLevelSelector(levels: List<Level>) {
    private val levels = levels.filter { it.status.equals("A", ignoreCase = true) }.distinctBy(Level::id)
    private val byId = this.levels.associateBy(Level::id)
    private val byParent = this.levels.groupBy(Level::superiorId)

    fun level(id: String): Level? = byId[id]

    fun hasChildren(id: String): Boolean = byParent[id].orEmpty().isNotEmpty()

    fun pathTo(id: String): List<Level> {
        val path = mutableListOf<Level>()
        val visited = mutableSetOf<String>()
        var current = byId[id]
        while (current != null && visited.add(current.id)) {
            path += current
            current = byId[current.superiorId]
        }
        return path.asReversed()
    }

    fun choices(query: String, parentId: String?): List<OplLevelChoice> {
        val normalized = query.trim().lowercase()
        val source = if (normalized.isNotEmpty()) {
            levels.filter { level ->
                listOf(level.name, level.description, level.machineId, level.ownerName)
                    .filterNotNull().any { it.lowercase().contains(normalized) }
            }
        } else if (parentId == null) {
            levels.filter { it.superiorId.isBlank() || it.superiorId == "0" || it.superiorId !in byId }
        } else {
            byParent[parentId].orEmpty()
        }
        return source.sortedWith(compareBy(Level::depth, Level::name)).map { level ->
            OplLevelChoice(
                id = level.id,
                title = level.name,
                subtitle = listOfNotNull(level.machineId, level.ownerName).joinToString(" · ")
                    .ifBlank { level.description },
                hasChildren = hasChildren(level.id),
            )
        }
    }
}
