package com.ih.osm.features.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyText

@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnatomyText(stringResource(R.string.notes_title), style = MaterialTheme.typography.headlineSmall)
    }
}
