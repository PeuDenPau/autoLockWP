package com.lockcanvas.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Typeface
import com.lockcanvas.app.data.ColorLayer
import com.lockcanvas.app.data.GroupLayer
import com.lockcanvas.app.data.ImageLayer
import com.lockcanvas.app.data.Layer
import com.lockcanvas.app.data.TextLayer

/**
 * Renders a preset into a bitmap. Runs both in the editor preview and in the
 * background render used to update the lock screen wallpaper. Pure function of
 * (preset, size, variables): no allocation is shared across renders except the
 * AssetStore caches.
 */
class Renderer(private val assets: AssetStore) {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun render(preset: com.lockcanvas.app.data.Preset, width: Int, height: Int, vars: Map<String, String>): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), colorPaint.apply {
            color = android.graphics.Color.BLACK
        })
        drawLayer(canvas, preset.root, width, height, vars, offsetX = 0f, offsetY = 0f, parentAlpha = 1f)
        return bmp
    }

    private fun drawLayer(
        canvas: Canvas,
        layer: Layer,
        w: Int,
        h: Int,
        vars: Map<String, String>,
        offsetX: Float,
        offsetY: Float,
        parentAlpha: Float,
    ) {
        if (!layer.visible || layer.alpha <= 0f) return
        val alpha = (parentAlpha * layer.alpha).coerceIn(0f, 1f)
        when (layer) {
            is GroupLayer -> {
                val gx = offsetX + layer.x * w
                val gy = offsetY + layer.y * h
                val save = canvas.save()
                canvas.translate(gx, gy)
                for (child in layer.children) {
                    drawLayer(canvas, child, w, h, vars, 0f, 0f, alpha)
                }
                canvas.restoreToCount(save)
            }
            is ImageLayer -> drawImage(canvas, layer, w, h, offsetX, offsetY, alpha)
            is ColorLayer -> drawColor(canvas, layer, w, h, offsetX, offsetY, alpha)
            is TextLayer -> drawText(canvas, layer, w, h, vars, offsetX, offsetY, alpha)
        }
    }

    private fun drawImage(
        canvas: Canvas,
        layer: ImageLayer,
        w: Int,
        h: Int,
        offsetX: Float,
        offsetY: Float,
        alpha: Float,
    ) {
        val bmp = assets.bitmapOrNull(layer.asset) ?: return
        bitmapPaint.alpha = (alpha * 255).toInt()
        val dw = layer.width * w
        val dh = layer.height * h
        val ax = offsetX + layer.x * w
        val ay = offsetY + layer.y * h

        val save = canvas.save()
        canvas.translate(ax, ay)
        canvas.rotate(layer.rotation)
        canvas.translate(-dw * layer.pivotX, -dh * layer.pivotY)
        if (layer.fit == ImageLayer.Fit.FILL) {
            canvas.clipRect(0f, 0f, dw, dh)
        }

        val matrix = Matrix()
        val sx = dw / bmp.width
        val sy = dh / bmp.height
        matrix.setScale(sx, sy)
        when (layer.fit) {
            ImageLayer.Fit.STRETCH -> {}
            ImageLayer.Fit.FIT,
            ImageLayer.Fit.FILL -> {
                val s = if (layer.fit == ImageLayer.Fit.FIT) minOf(sx, sy) else maxOf(sx, sy)
                matrix.setScale(s, s)
                matrix.postTranslate((dw - bmp.width * s) / 2f, (dh - bmp.height * s) / 2f)
            }
        }
        canvas.drawBitmap(bmp, matrix, bitmapPaint)
        canvas.restoreToCount(save)
    }

    private fun drawColor(
        canvas: Canvas,
        layer: ColorLayer,
        w: Int,
        h: Int,
        offsetX: Float,
        offsetY: Float,
        alpha: Float,
    ) {
        colorPaint.color = layer.color.toInt()
        colorPaint.alpha = ((alpha * 255) * (android.graphics.Color.alpha(layer.color.toInt()) / 255f)).toInt()
        val dw = layer.width * w
        val dh = layer.height * h
        val ax = offsetX + layer.x * w
        val ay = offsetY + layer.y * h
        val save = canvas.save()
        canvas.translate(ax, ay)
        canvas.rotate(layer.rotation)
        canvas.translate(-dw * layer.pivotX, -dh * layer.pivotY)
        canvas.drawRect(0f, 0f, dw, dh, colorPaint)
        canvas.restoreToCount(save)
    }

    private fun drawText(
        canvas: Canvas,
        layer: TextLayer,
        w: Int,
        h: Int,
        vars: Map<String, String>,
        offsetX: Float,
        offsetY: Float,
        alpha: Float,
    ) {
        val content = Variables.expand(layer.template, vars)
        if (content.isEmpty()) return

        textPaint.color = layer.color.toInt()
        textPaint.alpha = (alpha * 255).toInt()
        textPaint.textSize = layer.fontSize * h
        val typeface = layer.font?.let { assets.openFont(it) } ?: Typeface.DEFAULT
        textPaint.typeface = if (layer.bold) Typeface.create(typeface, Typeface.BOLD) else typeface
        textPaint.letterSpacing = layer.letterSpacing

        val layout = StaticLayout.Builder.obtain(content, 0, content.length, textPaint, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        var maxLineWidth = 0f
        for (i in 0 until layout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, layout.getLineWidth(i))
        }
        val textW = maxLineWidth.coerceAtLeast(1f)
        val textH = layout.height.toFloat()
        val ax = offsetX + layer.x * w
        val ay = offsetY + layer.y * h
        val boxLeft = (w - textW) / 2f

        val save = canvas.save()
        canvas.translate(ax, ay)
        canvas.rotate(layer.rotation)
        canvas.translate(-(boxLeft + textW * layer.pivotX), -textH * layer.pivotY)
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }
}
