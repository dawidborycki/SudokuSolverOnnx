# SudokuSolverOnnx

## Overview

A Kotlin Android application that detects a Sudoku grid in an image, recognizes its digits with a bundled ONNX model, and overlays a computed solution on the input. It demonstrates how to connect OpenCV preprocessing, on-device inference, deterministic solving, and an Android user interface.

The current interface uses bundled example bitmaps. Recognition and solving run locally; live camera capture and gallery import are not implemented.

## Technical focus

- Kotlin, Android views, and lifecycle coroutines
- OpenCV contour detection and perspective rectification
- Grayscale image preprocessing and batched digit recognition
- ONNX Runtime Android with a bundled FP32 model
- Backtracking and image-space result overlays

## Processing workflow

`Input bitmap → detect grid → rectify perspective → recognize 81 cells → solve board → overlay solution`

1. OpenCV finds a quadrilateral grid and rectifies it to a 450 × 450 image.
2. The engine crops the 81 cells, converts them to grayscale, resizes each to 28 × 28, and normalizes the pixel values.
3. ONNX Runtime processes all cells in one batch. The model distinguishes blank cells and digits 1–9; predictions below the configured confidence threshold are treated as blank.
4. A Kotlin backtracking solver fills the recognized board. The engine maps the added digits back onto the original image using the inverse perspective transform.

| Component | Responsibility |
| --- | --- |
| [MainActivity.kt](app/src/main/java/com/arm/sudokusolveronnx/MainActivity.kt) | Loads bundled images, dispatches processing to a background coroutine, and displays results |
| [SudokuEngine.kt](app/src/main/java/com/arm/sudokusolveronnx/SudokuEngine.kt) | Grid detection, preprocessing, ONNX inference, and solution overlays |
| [SudokuSolver.kt](app/src/main/java/com/arm/sudokusolveronnx/SudokuSolver.kt) | Backtracking solver for a 9 × 9 board |
| [BoardRenderer.kt](app/src/main/java/com/arm/sudokusolveronnx/BoardRenderer.kt) | Renders a separate solved-board bitmap |

The model is stored at [app/src/main/assets/sudoku_digitnet_android.onnx](app/src/main/assets/sudoku_digitnet_android.onnx). It accepts FP32 input named `input` with shape `[N, 1, 28, 28]` and returns ten class scores per cell. The application uses `N = 81` and the default ONNX Runtime session; it does not configure GPU or NNAPI acceleration.

## Getting started

### Recorded project configuration

| Setting | Version |
| --- | --- |
| Android Gradle Plugin | 8.7.3 |
| Gradle wrapper | 8.9 |
| Kotlin | 1.9.24 |
| Compile / target SDK | 35 / 34 |
| Minimum Android version | Android 7.0, API 24 |
| ONNX Runtime Android | 1.18.0 |
| OpenCV | 4.10.0 |

Use an Android Studio version compatible with the recorded Android Gradle Plugin, **JDK 17**, and Android SDK Platform 35 with the required SDK Build Tools. Although the project targets Java/Kotlin bytecode version 11, the Gradle build requires JDK 17; see the [AGP 8.7 compatibility requirements](https://developer.android.com/build/releases/agp-8-7-0-release-notes).

1. Clone this repository and open its root directory in Android Studio.
2. Select JDK 17 as the Gradle JDK, install the required SDK components, and synchronize the project.
3. Run the `app` configuration on a device or emulator running API 24 or later.

To build a debug APK from the repository root:

```sh
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Dependency downloads require network access; the model and example images are bundled with the application.

### Usage

1. Tap **Load image** to select a random bundled camera-like Sudoku bitmap.
2. Tap **Solve** to run recognition and solving.
3. Inspect the output image: added digits are shown in green over cells recognized as blank.

Ten camera-like and ten clean images are included. The current button selects the camera-like pool; switching to the clean pool requires changing the `useCameraLike` argument in `MainActivity.kt`.

## Validation and limitations

This is an educational integration example. The existing tests are Android project-template checks; there are no end-to-end recognition tests, accuracy measurements, or reproducible performance benchmarks.

Recognition errors can affect solving. The solver returns the first completion it finds and does not verify uniqueness or fully validate the recognized givens. Native-resource cleanup, lifecycle handling, and inference-error recovery also need review before production use.

## Related material

- [Deploy optimized ML models with ONNX Runtime on Arm platforms — Android deployment chapter](https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/onnx/08_android/): the public Arm Learning Path links directly to this repository as its companion code and covers the Android integration workflow.
