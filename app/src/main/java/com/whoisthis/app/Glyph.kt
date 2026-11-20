package com.whoisthis.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

enum class Symbol { Scan, People, Controls, Add, Flip, Pause, Play, Close, Delete, Shield, Check, Camera }

@Composable
fun Glyph(symbol: Symbol, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x, y), Offset(x2, y2), 1.7f, StrokeCap.Round)
            fun path(vararg points: Float) {
                drawPath(Path().apply {
                    moveTo(points[0], points[1])
                    for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
                }, color, style = Stroke(1.7f, cap = StrokeCap.Round))
            }
            when (symbol) {
                Symbol.Scan -> {
                    path(8f, 3f, 3f, 3f, 3f, 8f)
                    path(16f, 3f, 21f, 3f, 21f, 8f)
                    path(3f, 16f, 3f, 21f, 8f, 21f)
                    path(21f, 16f, 21f, 21f, 16f, 21f)
                    drawCircle(color, 3f, Offset(12f, 10f), style = Stroke(1.5f))
                    path(7f, 17f, 9f, 15f, 15f, 15f, 17f, 17f)
                }
                Symbol.People -> {
                    drawCircle(color, 3.2f, Offset(9f, 8f), style = Stroke(1.7f))
                    path(3f, 20f, 3f, 18f, 5f, 15f, 13f, 15f, 15f, 18f, 15f, 20f)
                    path(16f, 5f, 19f, 6f, 19f, 10f, 17f, 11f)
                    path(18f, 15f, 21f, 17f, 21f, 20f)
                }
                Symbol.Controls -> {
                    for (y in listOf(5f, 12f, 19f)) line(3f, y, 21f, y)
                    drawCircle(Palette.Background, 2.6f, Offset(8f, 5f))
                    drawCircle(color, 2.6f, Offset(8f, 5f), style = Stroke(1.7f))
                    drawCircle(Palette.Background, 2.6f, Offset(16f, 12f))
                    drawCircle(color, 2.6f, Offset(16f, 12f), style = Stroke(1.7f))
                    drawCircle(Palette.Background, 2.6f, Offset(10f, 19f))
                    drawCircle(color, 2.6f, Offset(10f, 19f), style = Stroke(1.7f))
                }
                Symbol.Add -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
                Symbol.Flip -> {
                    path(4f, 9f, 7f, 5f, 18f, 5f, 21f, 9f)
                    path(17f, 9f, 21f, 9f, 21f, 5f)
                    path(20f, 15f, 17f, 19f, 6f, 19f, 3f, 15f)
                    path(3f, 19f, 3f, 15f, 7f, 15f)
                }
                Symbol.Pause -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
                Symbol.Play -> path(8f, 4f, 20f, 12f, 8f, 20f, 8f, 4f)
                Symbol.Close -> { line(6f, 6f, 18f, 18f); line(6f, 18f, 18f, 6f) }
                Symbol.Delete -> {
                    line(4f, 6f, 20f, 6f)
                    path(8f, 6f, 8f, 3f, 16f, 3f, 16f, 6f)
                    path(6f, 6f, 7f, 21f, 17f, 21f, 18f, 6f)
                    line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f)
                }
                Symbol.Shield -> {
                    path(12f, 2f, 21f, 6f, 20f, 15f, 17f, 19f, 12f, 22f, 7f, 19f, 4f, 15f, 3f, 6f, 12f, 2f)
                    path(8f, 12f, 11f, 15f, 16f, 9f)
                }
                Symbol.Check -> path(5f, 12f, 10f, 17f, 20f, 6f)
                Symbol.Camera -> {
                    path(3f, 7f, 7f, 7f, 9f, 4f, 15f, 4f, 17f, 7f, 21f, 7f, 21f, 20f, 3f, 20f, 3f, 7f)
                    drawCircle(color, 4f, Offset(12f, 13f), style = Stroke(1.7f))
                }
            }
        }
    }
}
