package com.ih.osm.designsystem.anatomy

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ih.osm.designsystem.preview.AnatomyPreview
import com.ih.osm.designsystem.preview.PreviewComponent
import com.ih.osm.R

@Immutable
sealed interface AnatomyImageSource {
    @Immutable
    data class Url(val value: String) : AnatomyImageSource

    @Immutable
    data class Resource(@DrawableRes val drawableResId: Int) : AnatomyImageSource

    @Immutable
    data class PainterSource(val painter: Painter) : AnatomyImageSource
}

@Composable
fun AnatomyImage(
    source: AnatomyImageSource?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )

        when (source) {
            null -> Unit
            is AnatomyImageSource.PainterSource -> Image(
                painter = source.painter,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )

            is AnatomyImageSource.Resource -> AsyncImage(
                model = source.drawableResId,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )

            is AnatomyImageSource.Url -> AsyncImage(
                model = source.value.takeIf(String::isNotBlank),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

@PreviewComponent
@Composable
private fun AnatomyImagePreview() = AnatomyPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnatomyImage(
            source = AnatomyImageSource.Resource(R.drawable.anatomy_preview_equipment),
            contentDescription = "Equipo industrial",
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
        )
        AnatomyImage(
            source = AnatomyImageSource.Url("https://example.com/equipment.jpg"),
            contentDescription = "Imagen remota",
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
        )
    }
}
