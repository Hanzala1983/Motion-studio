#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <vector>
#include "native_compositor.h"
#include "yuv_convert.h"

#define LOG_TAG "NativeRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// JNI entry points backing com.example.render.NativeRenderer (Kotlin).
// Naming follows the standard Java_<package_with_underscores>_<Class>_<method>
// convention so the JVM can resolve these without an explicit
// System.loadLibrary registration table.

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_render_NativeRenderer_nativeEngineVersion(JNIEnv* env, jobject /* this */) {
    // Simple liveness check the Kotlin side can call at startup to confirm
    // the native library loaded correctly, before relying on it for real
    // frame composition.
    return env->NewStringUTF("motionstudio-native-renderer/0.1");
}

// Composites the given layer bitmaps onto outBitmap (an already-allocated,
// ARGB_8888 Android Bitmap passed from Kotlin) and returns nothing — the
// Bitmap's pixels are modified in place, so the Kotlin side can hand the
// same reused Bitmap into the encoder path exactly as it does today.
//
// layerBitmaps: Bitmap[] (ARGB_8888) — one per layer, already decoded /
//   rasterized on the Kotlin side (image decode, shape-to-bitmap, or the
//   current video frame), in back-to-front draw order.
// opacities, offsetsX/Y, rotations, scalesX/Y: float[] parallel arrays,
//   one entry per layer, matching layerBitmaps' order/length.
extern "C" JNIEXPORT void JNICALL
Java_com_example_render_NativeRenderer_nativeCompositeFrame(
        JNIEnv* env, jobject /* this */,
        jobject outBitmap,
        jobjectArray layerBitmaps,
        jfloatArray opacities,
        jfloatArray offsetsX,
        jfloatArray offsetsY,
        jfloatArray rotations,
        jfloatArray scalesX,
        jfloatArray scalesY
) {
    AndroidBitmapInfo outInfo;
    if (AndroidBitmap_getInfo(env, outBitmap, &outInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get output bitmap info");
        return;
    }
    if (outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Output bitmap must be ARGB_8888, got format %d", outInfo.format);
        return;
    }
    void* outPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, outBitmap, &outPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock output bitmap pixels");
        return;
    }

    jsize layerCount = env->GetArrayLength(layerBitmaps);
    std::vector<NativeLayerBitmap> layers;
    layers.reserve(layerCount);

    // Keep locked source bitmaps' info/pixel pointers alive until we're
    // done compositing, then unlock them all at the end.
    std::vector<jobject> lockedBitmaps;
    std::vector<void*> lockedPixels;

    jfloat* opacityArr = env->GetFloatArrayElements(opacities, nullptr);
    jfloat* offXArr = env->GetFloatArrayElements(offsetsX, nullptr);
    jfloat* offYArr = env->GetFloatArrayElements(offsetsY, nullptr);
    jfloat* rotArr = env->GetFloatArrayElements(rotations, nullptr);
    jfloat* scaleXArr = env->GetFloatArrayElements(scalesX, nullptr);
    jfloat* scaleYArr = env->GetFloatArrayElements(scalesY, nullptr);

    for (jsize i = 0; i < layerCount; i++) {
        jobject bmp = env->GetObjectArrayElement(layerBitmaps, i);
        if (bmp == nullptr) continue;

        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bmp, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("Failed to get info for layer %d", (int) i);
            continue;
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
            LOGE("Layer %d bitmap must be ARGB_8888, got format %d — skipping", (int) i, info.format);
            continue;
        }
        void* pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bmp, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("Failed to lock pixels for layer %d", (int) i);
            continue;
        }
        lockedBitmaps.push_back(bmp);
        lockedPixels.push_back(pixels);

        NativeLayerBitmap layer;
        layer.kind = NativeLayerKind::Image; // blend path doesn't currently depend on kind
        layer.rgbaPixels = static_cast<const uint8_t*>(pixels);
        layer.width = (int) info.width;
        layer.height = (int) info.height;
        layer.opacity = opacityArr[i];
        layer.transform.offsetX = offXArr[i];
        layer.transform.offsetY = offYArr[i];
        layer.transform.rotationDegrees = rotArr[i];
        layer.transform.scaleX = scaleXArr[i];
        layer.transform.scaleY = scaleYArr[i];
        layers.push_back(layer);
    }

    compositeFrame(
            static_cast<uint8_t*>(outPixels),
            (int) outInfo.width,
            (int) outInfo.height,
            layers
    );

    env->ReleaseFloatArrayElements(opacities, opacityArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(offsetsX, offXArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(offsetsY, offYArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(rotations, rotArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(scalesX, scaleXArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(scalesY, scaleYArr, JNI_ABORT);

    for (size_t i = 0; i < lockedBitmaps.size(); i++) {
        AndroidBitmap_unlockPixels(env, lockedBitmaps[i]);
    }
    AndroidBitmap_unlockPixels(env, outBitmap);
}

// Converts an ARGB_8888 Bitmap's pixels to YUV420 (NV12 or I420) into the
// provided direct ByteBuffer, matching VideoRenderer.kt's old bitmapToYuv
// but running natively — this was the single largest per-frame cost in
// the pure-Kotlin export pipeline.
extern "C" JNIEXPORT void JNICALL
Java_com_example_render_NativeRenderer_nativeBitmapToYuv420(
        JNIEnv* env, jobject /* this */,
        jobject bitmap,
        jobject outYuvDirectBuffer,
        jboolean semiPlanar
) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("nativeBitmapToYuv420: failed to get bitmap info");
        return;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("nativeBitmapToYuv420: bitmap must be ARGB_8888, got format %d", info.format);
        return;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("nativeBitmapToYuv420: failed to lock pixels");
        return;
    }
    auto* outYuv = static_cast<uint8_t*>(env->GetDirectBufferAddress(outYuvDirectBuffer));
    if (outYuv == nullptr) {
        LOGE("nativeBitmapToYuv420: output buffer must be a direct ByteBuffer");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    rgbaToYuv420(
            static_cast<const uint8_t*>(pixels),
            (int) info.width,
            (int) info.height,
            outYuv,
            semiPlanar
    );

    AndroidBitmap_unlockPixels(env, bitmap);
}
