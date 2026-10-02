package com.sentinel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TACUNS brand mark: a bevelled hexagon lit from the top-left with a raised "T".
 * Drawn in code on a 200×200 grid, so it stays sharp at any size and needs no image file.
 */
@Composable
fun TacunsLogoMark(modifier: Modifier = Modifier, size: Dp = 84.dp) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "TACUNS" }
    ) {
        val s = this.size.minDimension / 200f
        fun p(vararg xy: Float) = Path().apply {
            moveTo(xy[0] * s, xy[1] * s)
            var i = 2
            while (i < xy.size) { lineTo(xy[i] * s, xy[i + 1] * s); i += 2 }
            close()
        }

        // Soft warm glow behind the badge.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFE2541B).copy(alpha = 0.45f), Color.Transparent),
                center = Offset(100f * s, 112f * s), radius = 104f * s
            ),
            radius = 104f * s, center = Offset(100f * s, 112f * s)
        )

        // Bevel faces — brighter where the light hits, darker underneath.
        drawPath(p(25.5f, 57f, 100f, 14f, 100f, 38f, 46.3f, 69f), Color(0xFFFFC27A))
        drawPath(p(100f, 14f, 174.5f, 57f, 153.7f, 69f, 100f, 38f), Color(0xFFFFA646))
        drawPath(p(174.5f, 57f, 174.5f, 143f, 153.7f, 131f, 153.7f, 69f), Color(0xFFE36E1C))
        drawPath(p(174.5f, 143f, 100f, 186f, 100f, 162f, 153.7f, 131f), Color(0xFFA9420E))
        drawPath(p(100f, 186f, 25.5f, 143f, 46.3f, 131f, 100f, 162f), Color(0xFFC25214))
        drawPath(p(25.5f, 143f, 25.5f, 57f, 46.3f, 69f, 46.3f, 131f), Color(0xFFF28C2E))

        // Top face with a gentle gloss on its upper half.
        drawPath(
            p(100f, 38f, 153.7f, 69f, 153.7f, 131f, 100f, 162f, 46.3f, 131f, 46.3f, 69f),
            Brush.linearGradient(
                listOf(Color(0xFFFFA23A), Color(0xFFE2541B)),
                start = Offset(46f * s, 38f * s), end = Offset(154f * s, 162f * s)
            )
        )
        drawPath(
            p(100f, 38f, 153.7f, 69f, 153.7f, 100f, 46.3f, 100f, 46.3f, 69f),
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                startY = 38f * s, endY = 100f * s
            )
        )
        // Thin light rim along the lit edges.
        drawPath(
            Path().apply {
                moveTo(25.5f * s, 143f * s); lineTo(25.5f * s, 57f * s)
                lineTo(100f * s, 14f * s); lineTo(174.5f * s, 57f * s)
            },
            Color.White.copy(alpha = 0.35f),
            style = Stroke(width = 1.6f * s)
        )

        drawRaisedT(s)
    }
}

private fun DrawScope.drawRaisedT(s: Float) {
    val t = Path().apply {
        moveTo(68f * s, 67f * s); lineTo(132f * s, 67f * s); lineTo(132f * s, 86f * s)
        lineTo(109.5f * s, 86f * s); lineTo(109.5f * s, 135f * s); lineTo(90.5f * s, 135f * s)
        lineTo(90.5f * s, 86f * s); lineTo(68f * s, 86f * s); close()
    }
    // Stacked copies build the side wall, so the letter reads as solid, not a flat shadow.
    for (i in 7 downTo 1) {
        translate(left = i * 0.8f * s, top = i * 1.0f * s) {
            drawPath(t, if (i == 7) Color(0xFF5E2306) else Color(0xFF8A3409))
        }
    }
    drawPath(
        t,
        Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE3CC)), startY = 67f * s, endY = 135f * s)
    )
}

/** "TACUNS™" wordmark in white with the brand-orange trademark sign. */
@Composable
fun TacunsWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 22.sp) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Text(
            "TACUNS",
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            color = SecurePalette.TextMain,
            letterSpacing = (fontSize.value * 0.12f).sp
        )
        Text(
            "™",
            fontSize = (fontSize.value * 0.38f).sp,
            fontWeight = FontWeight.Bold,
            color = SecurePalette.Orange,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}
