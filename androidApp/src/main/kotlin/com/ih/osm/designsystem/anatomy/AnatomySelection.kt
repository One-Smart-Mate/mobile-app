package com.ih.osm.designsystem.anatomy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ih.osm.R

data class AnatomySelectionItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val hasChildren: Boolean = false,
)

@Composable
fun AnatomySelectionField(
    label: String,
    value: String?,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnatomyText(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            properties = AnatomyTextProperties(
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(enabled = enabled, onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                AnatomyText(
                    text = value ?: placeholder,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(
                        color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    ),
                )
                if (value != null && onClear != null) {
                    IconButton(onClick = onClear, enabled = enabled) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                } else {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                }
            }
        }
        supporting?.let {
            AnatomyText(
                text = it,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

/** Extracted from Create Card: one sheet, search, breadcrumbs and scrollable choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnatomySelectionBottomSheet(
    title: String,
    query: String,
    searchPlaceholder: String,
    items: List<AnatomySelectionItem>,
    selectedItemId: String?,
    onQueryChanged: (String) -> Unit,
    onSelectItem: (String) -> Unit,
    onDismiss: () -> Unit,
    breadcrumbs: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    customContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)
                .widthIn(max = 720.dp).align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnatomyText(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.Bold),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.create_card_close_sheet))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (customContent != null) {
                customContent()
                return@Column
            }
            AnatomySearch(value = query, onValueChange = onQueryChanged, placeholder = searchPlaceholder)
            if (query.isBlank() && breadcrumbs != null) {
                Spacer(Modifier.height(12.dp))
                breadcrumbs()
            }
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnatomyText(
                        text = stringResource(R.string.create_card_no_results),
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = AnatomySelectionItem::id) { item ->
                        SelectionSheetItem(item, item.id == selectedItemId) { onSelectItem(item.id) }
                    }
                }
            }
            footer?.let {
                it()
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun AnatomyLevelBreadcrumbs(path: List<AnatomySelectionItem>, onNavigate: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { onNavigate(null) },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Business, contentDescription = null, modifier = Modifier.size(16.dp))
                AnatomyText(stringResource(R.string.create_card_level_root), style = MaterialTheme.typography.labelMedium)
            }
        }
        path.forEach { level ->
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            Surface(
                onClick = { onNavigate(level.id) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                AnatomyText(
                    text = level.title,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    properties = AnatomyTextProperties(maxLines = 1),
                )
            }
        }
    }
}

@Composable
private fun SelectionSheetItem(item: AnatomySelectionItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnatomyText(item.title, style = MaterialTheme.typography.bodyMedium,
                    properties = AnatomyTextProperties(fontWeight = FontWeight.SemiBold))
                item.subtitle?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(2.dp))
                    AnatomyText(it, style = MaterialTheme.typography.bodySmall,
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else if (item.hasChildren) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    }
}
