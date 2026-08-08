package com.aidesktop.os.ui.desktop.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aidesktop.os.domain.model.AppKind
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.DesktopBackground
import com.aidesktop.os.ui.theme.DesktopSurface
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.TextSecondary

/**
 * Default desktop wallpaper — a procedurally drawn night mountain/water scene
 * (sky gradient + soft moon glow + layered mountain silhouettes + a mirrored
 * reflection on the water) in the app's own dark-navy/blue palette. Everything
 * is plain Canvas drawing, no bitmap asset, so it always renders and never
 * needs a network fetch. Swappable later for a user-chosen photo via Settings.
 */
@Composable
fun DesktopWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.62f

        // Sky
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(DesktopBackground, DesktopSurfaceElevated, DesktopSurfaceHigh),
                startY = 0f,
                endY = horizonY
            ),
            size = androidx.compose.ui.geometry.Size(w, horizonY)
        )

        // Soft moon glow, upper area
        val moonCenter = Offset(w * 0.74f, h * 0.16f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentBlue.copy(alpha = 0.35f), Color.Transparent),
                center = moonCenter,
                radius = w * 0.32f
            ),
            radius = w * 0.32f,
            center = moonCenter
        )
        drawCircle(
            color = TextSecondary.copy(alpha = 0.55f),
            radius = w * 0.028f,
            center = moonCenter
        )

        // Far mountain layer (lighter, further back)
        fun mountainPath(baseY: Float, amplitude: Float, seedShift: Float): Path = Path().apply {
            moveTo(0f, baseY)
            val step = w / 6f
            for (i in 0..6) {
                val x = i * step
                val peak = baseY - amplitude * (0.4f + 0.6f * kotlin.math.abs(
                    kotlin.math.sin((i * 1.3f) + seedShift)
                ))
                lineTo(x, peak)
            }
            lineTo(w, horizonY)
            lineTo(0f, horizonY)
            close()
        }

        drawPath(
            path = mountainPath(baseY = horizonY - h * 0.02f, amplitude = h * 0.14f, seedShift = 0.6f),
            color = DesktopSurfaceHigh.copy(alpha = 0.9f)
        )
        drawPath(
            path = mountainPath(baseY = horizonY, amplitude = h * 0.20f, seedShift = 2.1f),
            color = DesktopSurface
        )

        // Water below the horizon
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(DesktopSurfaceElevated, DesktopBackground),
                startY = horizonY,
                endY = h
            ),
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(w, h - horizonY)
        )

        // Mirrored mountain reflection, softly faded into the water
        withTransform({
            scale(scaleX = 1f, scaleY = -1f, pivot = Offset(0f, horizonY))
        }) {
            drawPath(
                path = mountainPath(baseY = horizonY, amplitude = h * 0.20f, seedShift = 2.1f),
                brush = Brush.verticalGradient(
                    colors = listOf(DesktopSurface.copy(alpha = 0.45f), Color.Transparent),
                    startY = horizonY,
                    endY = horizonY + h * 0.22f
                )
            )
        }

        // Subtle horizontal light shimmer on the water, under the moon
        val shimmerX = moonCenter.x
        val rowCount = 5
        for (i in 0 until rowCount) {
            val y = horizonY + (h - horizonY) * (0.15f + i * 0.16f)
            val rowWidth = w * (0.10f - i * 0.012f).coerceAtLeast(0.03f)
            drawLine(
                color = AccentBlue.copy(alpha = 0.18f - i * 0.03f),
                start = Offset(shimmerX - rowWidth, y),
                end = Offset(shimmerX + rowWidth, y),
                strokeWidth = (2.5f - i * 0.3f).coerceAtLeast(1f)
            )
        }
    }
}

@Composable
fun DesktopIconsGrid(
    apps: List<Triple<AppKind, String, ImageVector>>,
    onIconDoubleClick: (AppKind) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = modifier
            .width(84.dp)
            .padding(top = 12.dp, start = 8.dp)
    ) {
        items(apps) { (kind, name, icon) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clickable { onIconDoubleClick(kind) }
            ) {
                AppTileIcon(kind = kind, icon = icon, size = 32.dp)
                Text(
                    text = name,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
