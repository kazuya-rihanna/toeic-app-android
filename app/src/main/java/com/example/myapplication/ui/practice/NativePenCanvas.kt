package com.example.myapplication.ui.practice

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kr.neolab.sdk.ink.structure.Dot

data class PenStroke(
    val dots: List<Dot>
)

@Composable
fun NativePenCanvas(
    strokes: List<PenStroke>,
    currentStroke: List<Dot>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    strokeColor: Color = Color.Black,
    orientation: Int = 0
) {
    android.util.Log.d("NativePenCanvas", "Recomposing: strokes=${strokes.size}, currentDots=${currentStroke.size}, orientation=$orientation")
    
    // Transform coordinates based on orientation
    fun getTransformedCoords(dot: Dot): Pair<Float, Float> {
        return when (orientation) {
            1 -> Pair(dot.y, -dot.x)
            2 -> Pair(-dot.x, -dot.y)
            3 -> Pair(-dot.y, dot.x)
            else -> Pair(dot.x, dot.y)
        }
    }

    // Dynamic bounds calculation: マージンなしの厳密なバウンディングボックス
    val bounds = remember(strokes, currentStroke, orientation) {
        val allDots = strokes.flatMap { it.dots } + currentStroke
        if (allDots.isEmpty()) {
            android.util.Log.d("NativePenCanvas", "Bounds: NULL (empty)")
            null
        } else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            allDots.forEach { dot ->
                val (tx, ty) = getTransformedCoords(dot)
                minX = minOf(minX, tx)
                minY = minOf(minY, ty)
                maxX = maxOf(maxX, tx)
                maxY = maxOf(maxY, ty)
            }
            android.util.Log.d("NativePenCanvas", "Bounds: L=$minX, T=$minY, R=$maxX, B=$maxY")
            // マージンなし（余白ゼロ）
            Rect(minX, minY, maxX, maxY)
        }
    }

    // 書いた範囲に応じてキャンバスの表示サイズを決定 (scale=20, pixelPad=4px)
    val scale = 20f
    val pixelPad = 4f // アンチエイリアス用の最小余白（px）
    val density = LocalDensity.current
    val displayModifier = if (bounds != null) {
        val displayW = (bounds.width * scale + pixelPad * 2).coerceAtLeast(1f)
        val displayH = (bounds.height * scale + pixelPad * 2).coerceAtLeast(1f)
        with(density) {
            modifier.size(
                width = displayW.toDp(),
                height = displayH.toDp()
            )
        }
    } else {
        modifier.fillMaxSize()
    }

    Box(modifier = displayModifier.background(backgroundColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (bounds != null && canvasWidth > 0 && canvasHeight > 0) {
                // 直接線形変換: 書いた座標をキャンバスにぴったりマッピング
                // minOfによるアスペクト比保持/センタリングを使わないことで余白ゼロを実現
                val mapX = { x: Float -> (x - bounds.left) * scale + pixelPad }
                val mapY = { y: Float -> (y - bounds.top) * scale + pixelPad }

                strokes.forEach { stroke ->
                    if (stroke.dots.size > 1) {
                        val path = androidx.compose.ui.graphics.Path()
                        stroke.dots.forEachIndexed { index, dot ->
                            val (txCoords, tyCoords) = getTransformedCoords(dot)
                            if (index == 0) path.moveTo(mapX(txCoords), mapY(tyCoords))
                            else            path.lineTo(mapX(txCoords), mapY(tyCoords))
                        }
                        drawPath(path = path, color = strokeColor, style = Stroke(width = 2f))
                    }
                }
                if (currentStroke.size > 1) {
                    val path = androidx.compose.ui.graphics.Path()
                    currentStroke.forEachIndexed { index, dot ->
                        val (txCoords, tyCoords) = getTransformedCoords(dot)
                        if (index == 0) path.moveTo(mapX(txCoords), mapY(tyCoords))
                        else            path.lineTo(mapX(txCoords), mapY(tyCoords))
                    }
                    drawPath(path = path, color = strokeColor, style = Stroke(width = 2f))
                }
            }
        }
    }
}


private data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Utility to render a list of strokes to a Bitmap for OCR
 */
fun createBitmapFromStrokes(
    strokes: List<PenStroke>,
    width: Int = 800,
    height: Int = 300,
    orientation: Int = 0
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    // Transform helper for bitmap output
    fun getTransformedCoords(dot: Dot): Pair<Float, Float> {
        return when (orientation) {
            1 -> Pair(dot.y, -dot.x)
            2 -> Pair(-dot.x, -dot.y)
            3 -> Pair(-dot.y, dot.x)
            else -> Pair(dot.x, dot.y)
        }
    }

    val allDots = strokes.flatMap { it.dots }
    if (allDots.isEmpty()) return bitmap

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    allDots.forEach { dot ->
        val (tx, ty) = getTransformedCoords(dot)
        minX = minOf(minX, tx)
        minY = minOf(minY, ty)
        maxX = maxOf(maxX, tx)
        maxY = maxOf(maxY, ty)
    }
    
    val margin = 5f
    val bLeft = minX - margin
    val bTop = minY - margin
    val bWidth = (maxX + margin) - bLeft
    val bHeight = (maxY + margin) - bTop

    val scaleX = width.toFloat() / bWidth
    val scaleY = height.toFloat() / bHeight
    val scale = minOf(scaleX, scaleY)
    
    val offsetX = (width - bWidth * scale) / 2f - bLeft * scale
    val offsetY = (height - bHeight * scale) / 2f - bTop * scale

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    strokes.forEach { stroke ->
        if (stroke.dots.size > 1) {
            val path = Path()
            stroke.dots.forEachIndexed { index, dot ->
                val (txCoords, tyCoords) = getTransformedCoords(dot)
                val tx = txCoords * scale + offsetX
                val ty = tyCoords * scale + offsetY
                if (index == 0) path.moveTo(tx, ty)
                else path.lineTo(tx, ty)
            }
            canvas.drawPath(path, paint)
        }
    }

    return bitmap
}
