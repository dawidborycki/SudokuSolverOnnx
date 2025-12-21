package com.arm.sudokusolveronnx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import org.opencv.android.OpenCVLoader
class MainActivity : AppCompatActivity() {

    private lateinit var btnLoadRandom: Button
    private lateinit var btnSolve: Button
    private lateinit var txtStatus: TextView
    private lateinit var imgInput: ImageView
    private lateinit var imgOutput: ImageView
    private lateinit var engine: SudokuEngine

    private var currentBitmap: Bitmap? = null

    // Clean and camera-like pools (you copied these into res/drawable/)
    private val sudokuCleanImages = listOf(
        R.drawable.sudoku_01,
        R.drawable.sudoku_02,
        R.drawable.sudoku_03,
        R.drawable.sudoku_04,
        R.drawable.sudoku_05,
        R.drawable.sudoku_06,
        R.drawable.sudoku_07,
        R.drawable.sudoku_08,
        R.drawable.sudoku_09,
        R.drawable.sudoku_10,
    )

    private val sudokuCamImages = listOf(
        R.drawable.sudoku_cam_01,
        R.drawable.sudoku_cam_02,
        R.drawable.sudoku_cam_03,
        R.drawable.sudoku_cam_04,
        R.drawable.sudoku_cam_05,
        R.drawable.sudoku_cam_06,
        R.drawable.sudoku_cam_07,
        R.drawable.sudoku_cam_08,
        R.drawable.sudoku_cam_09,
        R.drawable.sudoku_cam_10,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLoadRandom = findViewById(R.id.btnLoadRandom)
        btnSolve = findViewById(R.id.btnSolve)
        txtStatus = findViewById(R.id.txtStatus)
        imgInput = findViewById(R.id.imgInput)
        imgOutput = findViewById(R.id.imgOutput)

        btnSolve.isEnabled = false

        val ok = OpenCVLoader.initLocal()
        txtStatus.text = if (ok) "OpenCV ready" else "OpenCV init failed"

        engine = SudokuEngine(this)

        btnLoadRandom.setOnClickListener {
            loadRandomSudokuImage(useCameraLike = true)
        }

        btnSolve.setOnClickListener {
            val bmp = currentBitmap ?: return@setOnClickListener
            txtStatus.text = "Solving..."
            btnSolve.isEnabled = false

            lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    engine.solveBitmap(bmp)
                }

                if (result.overlayBitmap != null) {
                    imgOutput.setImageBitmap(result.overlayBitmap)
                    txtStatus.text = "Solved"
                } else {
                    txtStatus.text = "Solve failed (recognition errors)"
                }

                btnSolve.isEnabled = true
            }
        }
    }

    private fun loadRandomSudokuImage(useCameraLike: Boolean) {
        val pool = if (useCameraLike) sudokuCamImages else sudokuCleanImages
        val resId = pool[Random.nextInt(pool.size)]

        val bmp = BitmapFactory.decodeResource(resources, resId)
        currentBitmap = bmp

        imgInput.setImageBitmap(bmp)
        imgOutput.setImageDrawable(null)

        btnSolve.isEnabled = true
        txtStatus.text = if (useCameraLike) "Loaded camera-like Sudoku" else "Loaded clean Sudoku"
    }
}