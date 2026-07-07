#pragma once
#include <cstdint>
#include <vector>
#include <string>

// Mirrors the subset of layer state that VideoRenderer.kt currently reads
// from LayerTransform / layerColors / layerStartTimes / layerEndTimes when
// drawing a single export frame. Kept intentionally simple (POD struct) so
// it's cheap to fill from JNI every frame without extra allocations.
struct NativeLayerTransform {
    float offsetX = 0.0f;
    float offsetY = 0.0f;
    float rotationDegrees = 0.0f;
    float scaleX = 1.0f;
    float scaleY = 1.0f;
};

enum class NativeLayerKind {
    Shape,
    Image,   // still image media layer
    Video,   // video media layer (frame supplied by caller for this timestamp)
};

// A single layer's worth of already-decoded RGBA8888 pixels, ready to be
// composited into the output frame. For Shape layers this is a pre-rendered
// tinted bitmap (mirrors renderImageVectorToBitmap in VideoRenderer.kt); for
// Image/Video layers it's the decoded frame at the current timestamp.
struct NativeLayerBitmap {
    NativeLayerKind kind = NativeLayerKind::Shape;
    const uint8_t* rgbaPixels = nullptr; // width*height*4 bytes, not owned
    int width = 0;
    int height = 0;
    float opacity = 1.0f;                // 0..1, from layer's opacity keyframes
    NativeLayerTransform transform;
    int blendMode = 0;                   // 0 = normal; extend as blend modes are ported
};

// Composites all provided layers (already ordered back-to-front by the
// caller) onto an RGBA8888 output buffer of size outWidth*outHeight*4,
// which the caller must pre-fill with the background color.
void compositeFrame(
        uint8_t* outRgba,
        int outWidth,
        int outHeight,
        const std::vector<NativeLayerBitmap>& layers
);
