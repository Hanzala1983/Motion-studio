# Native (C++) Rendering Engine — Migration Notes

## What's done (this pass)

- **Build wiring**: `app/build.gradle.kts` now builds a CMake native
  library (`arm64-v8a`, `x86_64` — add more ABIs later if needed).
- **Native compositor** (`app/src/main/cpp/native_compositor.{h,cpp}`):
  real affine-transform (offset/rotate/scale) + alpha-blended layer
  compositing, written in C++, unit-compiles cleanly with `g++ -std=c++17
  -Wall` (verified in this environment — the Android/JNI-specific file
  could not be compiled here since no NDK toolchain is available in this
  sandbox; verify that one with a real Android Studio / NDK build before
  trusting it).
- **YUV conversion** (`app/src/main/cpp/yuv_convert.{h,cpp}`): ports
  `VideoRenderer.kt`'s `bitmapToYuv` — the single biggest per-frame cost in
  the old pipeline — to native code. Also compiles cleanly standalone.
- **JNI bridge** (`app/src/main/cpp/native_bridge.cpp` +
  `com.example.render.NativeRenderer` on the Kotlin side): exposes
  `compositeFrame(...)` and `nativeBitmapToYuv420(...)` to Kotlin, with
  bitmap-format validation and a `nativeEngineVersion()` smoke test.
  `MainActivity.onCreate` now logs whether the native library loaded, as a
  first sanity check — **it does not yet touch real export/render logic.**

## What's NOT done yet (important)

`VideoRenderer.kt` still does everything the old way. Nothing in the app
actually calls `NativeRenderer.compositeFrame` or
`NativeRenderer.nativeBitmapToYuv420` from the real export path yet. This
first slice is deliberately scoped to "does the native library build,
load, and do correct pixel math in isolation" — not "the export button
now uses C++".

## Suggested next steps, in order

1. **Build and run on a real device/emulator with Android Studio.** This
   sandbox has no NDK, so the JNI/Android-specific file
   (`native_bridge.cpp`) has only been reviewed by eye, not compiled.
   Confirm `Log.i("NativeRenderer", ...)` shows the version string at
   app startup before trusting anything further.
2. **Migrate `bitmapToYuv` first** (smallest, highest-value, easiest to
   verify): in `VideoRenderer.kt`'s frame-export loop, replace the call to
   the Kotlin `bitmapToYuv(...)` with `NativeRenderer.nativeBitmapToYuv420`
   (remember: needs a **direct** `ByteBuffer`, not a `ByteArray` — you'll
   need to adjust how the encoder's input buffer is filled). Compare
   exported video byte-for-byte or visually against the old path on a few
   test projects before removing the old Kotlin function.
3. **Migrate layer compositing next**: replace the `Canvas`-based drawing
   in `drawShapeLayers` / `drawMediaLayers` with
   `NativeRenderer.compositeFrame`. This is the bigger, riskier change —
   go layer-type by layer-type (shapes first, then images, then video
   frames) and compare output at each step.
4. **Only after both of the above are verified working** should GPU
   (OpenGL ES / EGL) compositing be considered, as a separate follow-up —
   it changes the JNI surface again (native side would render to a
   `Surface`/`SurfaceTexture` instead of a CPU pixel buffer) and is a much
   larger, riskier change than the CPU-native step done here.

## Why this order

Each step is independently testable and revertible. Jumping straight to
OpenGL/EGL compositing would mean debugging shader/context/color-format
issues *and* JNI issues *and* export-pipeline issues all at once, with no
working intermediate checkpoint if something goes wrong.
