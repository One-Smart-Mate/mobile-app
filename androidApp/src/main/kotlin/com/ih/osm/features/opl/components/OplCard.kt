package com.ih.osm.features.opl.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyCard
import com.ih.osm.designsystem.anatomy.AnatomyCardStyle
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContentType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Read-only OPL summary. Navigation is optional and belongs to the hosting screen. */
@Composable
fun OplCard(
    opl: Opl,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnatomyText(
                        text = opl.title,
                        style = MaterialTheme.typography.titleMedium,
                        properties = AnatomyTextProperties(
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        ),
                    )
                    opl.typeName?.takeIf(String::isNotBlank)?.let { type ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            AnatomyText(
                                text = type,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                properties = AnatomyTextProperties(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                ),
                            )
                        }
                    }
                }
                // Do not imply an available detail screen when there is no navigation callback.
                if (onClick != null) Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }

            if (opl.isDownloaded) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    AnatomyText(stringResource(R.string.opl_downloaded),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }

            opl.objective?.takeIf(String::isNotBlank)?.let { objective ->
                Spacer(Modifier.height(4.dp))
                AnatomyText(
                    text = objective,
                    style = MaterialTheme.typography.bodyLarge,
                    properties = AnatomyTextProperties(maxLines = 3, overflow = TextOverflow.Ellipsis),
                )
            }

            Spacer(Modifier.height(4.dp))
            val locations = opl.levels.distinctBy { it.id }.map { it.name }.filter(String::isNotBlank)
            val locationSummary = if (locations.isEmpty()) {
                stringResource(R.string.opl_card_no_locations)
            } else {
                val visibleNames = locations.take(2).joinToString(" · ")
                val remaining = locations.size - 2
                if (remaining > 0) {
                    "$visibleNames · ${pluralStringResource(R.plurals.opl_card_more_locations, remaining, remaining)}"
                } else visibleNames
            }
            MetadataLine(icon = Icons.Outlined.LocationOn, text = locationSummary)

            Spacer(Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OplContentType.entries.forEach { type ->
                    val count = opl.content.count { it.type == type }
                    if (count > 0) ContentBadge(type, count)
                }
                if (opl.content.isEmpty()) {
                    AnatomyText(
                        text = stringResource(R.string.opl_card_no_content),
                        style = MaterialTheme.typography.bodySmall,
                        properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }

            val creator = opl.creatorName?.takeIf(String::isNotBlank)
            val updatedDate = opl.updatedAt.displayDate()
            val createdDate = opl.createdAt.displayDate()
            val date = updatedDate ?: createdDate
            if (creator != null || date != null) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    creator?.let { MetadataLine(Icons.Outlined.PersonOutline, it) }
                    date?.let {
                        MetadataLine(
                            Icons.Outlined.CalendarToday,
                            stringResource(
                                if (updatedDate != null) R.string.opl_card_updated else R.string.opl_card_created,
                                it,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnatomyText(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            properties = AnatomyTextProperties(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            ),
        )
    }
}

@Composable
private fun ContentBadge(type: OplContentType, count: Int) {
    val (icon, labelResource) = when (type) {
        OplContentType.TEXT -> Icons.AutoMirrored.Outlined.Subject to R.plurals.opl_card_texts
        OplContentType.IMAGE -> Icons.Outlined.Image to R.plurals.opl_card_images
        OplContentType.VIDEO -> Icons.Outlined.PlayCircleOutline to R.plurals.opl_card_videos
        OplContentType.PDF -> Icons.Outlined.PictureAsPdf to R.plurals.opl_card_pdfs
        OplContentType.UNKNOWN -> Icons.Outlined.Description to R.plurals.opl_card_files
    }
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AnatomyText(
                text = pluralStringResource(labelResource, count, count),
                style = MaterialTheme.typography.labelSmall,
                properties = AnatomyTextProperties(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

/** The API uses ISO date prefixes; omit malformed dates rather than showing raw values. */
private fun String?.displayDate(): String? = this?.takeIf { it.length >= 10 }?.let { raw ->
    runCatching {
        LocalDate.parse(raw.take(10)).format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()),
        )
    }.getOrNull()
}
