package com.arm.sudokusolveronnx

import android.graphics.*

object BoardRenderer {
    fun render(board: Array<IntArray>, cell: Int = 80, margin: Int = 24): Bitmap {
        val size = 9 * cell + 2 * margin
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val thin = Paint().apply { color = Color.BLACK; strokeWidth = 2f; isAntiAlias = true }
        val thick = Paint().apply { color = Color.BLACK; strokeWidth = 6f; isAntiAlias = true }

        // grid
        for (i in 0..9) {
            val p = if (i % 3 == 0) thick else thin
            val x = (margin + i * cell).toFloat()
            val y = (margin + i * cell).toFloat()
            canvas.drawLine(x, margin.toFloat(), x, (margin + 9 * cell).toFloat(), p)
            canvas.drawLine(margin.toFloat(), y, (margin + 9 * cell).toFloat(), y, p)
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = (cell * 0.62f)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val fm = textPaint.fontMetrics
        val textYOffset = (fm.ascent + fm.descent) / 2f

        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val v = board[r][c]
                if (v == 0) continue
                val cx = margin + c * cell + cell / 2f
                val cy = margin + r * cell + cell / 2f - textYOffset
                canvas.drawText(v.toString(), cx, cy, textPaint)
            }
        }
        return bmp
    }
}