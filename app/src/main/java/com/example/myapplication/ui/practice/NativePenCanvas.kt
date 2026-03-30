package com.example.myapplication.ui.practice

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
    strokeColor: Color = Color.Black
) {
    android.util.Log.d("NativePenCanvas", "Recomposing: strokes=${strokes.size}, currentDots=${currentStroke.size}")
    // Dynamic bounds calculation (similar to web PenCanvas.tsx)
    val bounds = remember(strokes, currentStroke) {
        val allDots = strokes.flatMap { it.dots } + currentStroke
        if (allDots.isEmpty()) {
            android.util.Log.d("NativePenCanvas", "Bounds: NULL (empty)")
            null
        } else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            allDots.forEach {
                minX = minOf(minX, it.x)
                minY = minOf(minY, it.y)
                maxX = maxOf(maxX, it.x)
                maxY = maxOf(maxY, it.y)
            }
            android.util.Log.d("NativePenCanvas", "Bounds: L=$minX, T=$minY, R=$maxX, B=$maxY")
            // Add margin (similar to web's margin = 2)
            val margin = 5f
            Rect(minX - margin, minY - margin, maxX + margin, maxY + margin)
        }
    }

    Box(modifier = modifier.background(backgroundColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            if (bounds != null && canvasWidth > 0 && canvasHeight > 0) {
                val scaleX = canvasWidth / bounds.width
                val scaleY = canvasHeight / bounds.height
                val scale = minOf(scaleX, scaleY)
                
                // Center the drawing
                val offsetX = (canvasWidth - bounds.width * scale) / 2f - bounds.left * scale
                val offsetY = (canvasHeight - bounds.height * scale) / 2f - bounds.top * scale

                fun transformX(x: Float) = x * scale + offsetX
                fun transformY(y: Float) = y * scale + offsetY

                val drawStroke = { dots: List<Dot> ->
                    if (dots.size > 1) {
                        val path = androidx.compose.ui.graphics.Path()
                        dots.forEachIndexed { index, dot ->
                            val tx = transformX(dot.x)
                            val ty = transformY(dot.y)
                            if (index == 0) path.moveTo(tx, ty)
                            else path.lineTo(tx, ty)
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = 2f * scale / 20f) // Adjust width based on scale
                        )
                    }
                }

                strokes.forEach { drawStroke(it.dots) }
                drawStroke(currentStroke)
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
    height: Int = 300
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val allDots = strokes.flatMap { it.dots }
    if (allDots.isEmpty()) return bitmap

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    allDots.forEach {
        minX = minOf(minX, it.x)
        minY = minOf(minY, it.y)
        maxX = maxOf(maxX, it.x)
        maxY = maxOf(maxY, it.y)
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
                val tx = dot.x * scale + offsetX
                val ty = dot.y * scale + offsetY
                if (index == 0) path.moveTo(tx, ty)
                else path.lineTo(tx, ty)
            }
            canvas.drawPath(path, paint)
        }
    }

    return bitmap
}
