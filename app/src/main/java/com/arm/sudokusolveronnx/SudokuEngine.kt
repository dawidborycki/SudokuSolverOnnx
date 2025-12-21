package com.arm.sudokusolveronnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer

class SudokuEngine(
    private val context: Context,
    private val modelAssetName: String = "sudoku_digitnet_android.onnx",
    private val warpSize: Int = 450,
    private val inputSize: Int = 28,
    private val blankConfThreshold: Float = 0.65f
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open(modelAssetName).use { it.readBytes() }
        val opts = OrtSession.SessionOptions()
        session = env.createSession(modelBytes, opts)
    }

    private data class WarpResult(
        val warped: Mat,
        val H: Mat  // perspective transform from original -> warped
    )

    data class Result(
        val recognized: Array<IntArray>,
        val solved: Array<IntArray>?,
        val solvedBitmap: Bitmap?,
        val overlayBitmap: Bitmap?
    )

    fun solveBitmap(input: Bitmap): Result {
        // Bitmap -> Mat (BGR/RGBA depending on Utils, but works for our pipeline)
        val bgr = Mat()
        Utils.bitmapToMat(input, bgr)

        val warp = detectAndWarp(bgr) ?: return Result(emptyBoard(), null, null, null)

        val board = recognizeBoard(warp.warped)
        val solved = board.map { it.clone() }.toTypedArray()
        val ok = SudokuSolver.solve(solved)

        return if (ok) {
            val solvedGrid = BoardRenderer.render(solved) // keep if you still want it
            val overlay = makeOverlayBitmap(input, warp.H, board, solved)
            Result(board, solved, solvedGrid, overlay)
        } else {
            Result(board, null, null, null)
        }
    }

    private fun emptyBoard(): Array<IntArray> = Array(9) { IntArray(9) }

    private fun detectAndWarp(bgr: Mat): WarpResult? {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val thr = Mat()
        Imgproc.adaptiveThreshold(
            gray, thr, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            31, 7.0
        )

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(thr, thr, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 2)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(thr, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        if (contours.isEmpty()) return null
        contours.sortByDescending { Imgproc.contourArea(it) }

        var quad: MatOfPoint2f? = null
        for (i in 0 until minOf(20, contours.size)) {
            val c = contours[i]
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            if (approx.total().toInt() == 4) {
                quad = approx
                break
            }
        }
        if (quad == null) return null

        val pts = orderQuad(quad.toArray())
        val dst = arrayOf(
            Point(0.0, 0.0),
            Point((warpSize - 1).toDouble(), 0.0),
            Point((warpSize - 1).toDouble(), (warpSize - 1).toDouble()),
            Point(0.0, (warpSize - 1).toDouble())
        )

        val M = Imgproc.getPerspectiveTransform(MatOfPoint2f(*pts), MatOfPoint2f(*dst))
        val warped = Mat()
        Imgproc.warpPerspective(bgr, warped, M, Size(warpSize.toDouble(), warpSize.toDouble()))
        return WarpResult(warped = warped, H = M)
    }

    private fun orderQuad(pts: Array<Point>): Array<Point> {
        // order: TL, TR, BR, BL
        val sum = pts.map { it.x + it.y }
        val diff = pts.map { it.x - it.y }
        val tl = pts[sum.indices.minBy { sum[it] }]
        val br = pts[sum.indices.maxBy { sum[it] }]
        val tr = pts[diff.indices.maxBy { diff[it] }]
        val bl = pts[diff.indices.minBy { diff[it] }]
        return arrayOf(tl, tr, br, bl)
    }

    private fun recognizeBoard(warpedBgr: Mat): Array<IntArray> {
        val step = warpSize / 9
        val inputs = FloatArray(81 * 1 * inputSize * inputSize)

        var idx = 0
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val cell = warpedBgr.submat(r * step, (r + 1) * step, c * step, (c + 1) * step)
                val tensor = preprocessCell(cell) // FloatArray length = 1*28*28
                System.arraycopy(tensor, 0, inputs, idx * inputSize * inputSize, inputSize * inputSize)
                idx++
            }
        }

        val shape = longArrayOf(81, 1, inputSize.toLong(), inputSize.toLong())
        val fb = FloatBuffer.wrap(inputs)
        val inputTensor = OnnxTensor.createTensor(env, fb, shape)

        val out = session.run(mapOf("input" to inputTensor))
        val logits = out[0].value as Array<FloatArray> // [81][10]
        out.close()
        inputTensor.close()

        val board = Array(9) { IntArray(9) }
        for (i in 0 until 81) {
            val probs = softmax(logits[i])
            var bestK = 0
            var bestV = probs[0]
            for (k in 1 until probs.size) {
                if (probs[k] > bestV) { bestV = probs[k]; bestK = k }
            }
            val r = i / 9
            val c = i % 9
            board[r][c] = if (bestV < blankConfThreshold) 0 else bestK
        }
        return board
    }

    private fun preprocessCell(cellBgr: Mat): FloatArray {
        val gray = Mat()
        Imgproc.cvtColor(cellBgr, gray, Imgproc.COLOR_BGR2GRAY)

        val m = (0.12 * minOf(gray.rows(), gray.cols())).toInt()
        val cropped = gray.submat(m, gray.rows() - m, m, gray.cols() - m)

        val resized = Mat()
        Imgproc.resize(cropped, resized, Size(inputSize.toDouble(), inputSize.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        val out = FloatArray(inputSize * inputSize)
        var k = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val v = resized.get(y, x)[0].toFloat() / 255f
                out[k++] = (v - 0.5f) / 0.5f
            }
        }
        return out
    }

    private fun softmax(x: FloatArray): FloatArray {
        var max = x[0]
        for (v in x) if (v > max) max = v
        val e = FloatArray(x.size)
        var sum = 0f
        for (i in x.indices) {
            val v = kotlin.math.exp((x[i] - max).toDouble()).toFloat()
            e[i] = v
            sum += v
        }
        for (i in e.indices) e[i] /= (sum + 1e-12f)
        return e
    }

    private fun makeOverlayBitmap(
        originalBitmap: Bitmap,
        H: Mat,
        recognized: Array<IntArray>,
        solved: Array<IntArray>
    ): Bitmap {
        // Convert original to Mat (could be RGBA on Android)
        val original = Mat()
        Utils.bitmapToMat(originalBitmap, original)

        // Ensure original is BGR (3 channels)
        val originalBgr = Mat()
        if (original.channels() == 4) {
            Imgproc.cvtColor(original, originalBgr, Imgproc.COLOR_RGBA2BGR)
        } else {
            original.copyTo(originalBgr)
        }

        // Create layer in warped space (BGR)
        val layer = Mat.zeros(warpSize, warpSize, CvType.CV_8UC3)
        val step = warpSize / 9
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                if (recognized[r][c] != 0) continue
                val d = solved[r][c]
                val x = (c * step + step * 0.32).toInt()
                val y = (r * step + step * 0.72).toInt()
                Imgproc.putText(
                    layer, d.toString(),
                    Point(x.toDouble(), y.toDouble()),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.2,
                    Scalar(0.0, 200.0, 0.0), // green in BGR
                    2,
                    Imgproc.LINE_AA
                )
            }
        }

        // Inverse warp to original size (BGR)
        val invH = Mat()
        Core.invert(H, invH)

        val back = Mat.zeros(originalBgr.size(), CvType.CV_8UC3)
        Imgproc.warpPerspective(layer, back, invH, originalBgr.size())

        // Mask where back has pixels
        val mask = Mat()
        Imgproc.cvtColor(back, mask, Imgproc.COLOR_BGR2GRAY)
        Imgproc.threshold(mask, mask, 1.0, 255.0, Imgproc.THRESH_BINARY)

        // Blend (same size + same channels)
        val blended = Mat()
        Core.addWeighted(originalBgr, 0.6, back, 0.4, 0.0, blended)

        // Copy only where mask is present
        val outBgr = originalBgr.clone()
        blended.copyTo(outBgr, mask)

        // Convert back to bitmap (need RGBA for Android Bitmap)
        val outRgba = Mat()
        Imgproc.cvtColor(outBgr, outRgba, Imgproc.COLOR_BGR2RGBA)

        val outBmp = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outRgba, outBmp)
        return outBmp
    }
}