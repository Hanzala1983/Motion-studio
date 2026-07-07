#pragma once
#include <cstdint>

// Converts an RGBA8888 buffer (width*height*4 bytes) to YUV420 for
// MediaCodec input, matching VideoRenderer.kt's bitmapToYuv but running in
// native code instead of a per-pixel Kotlin loop — this conversion was the
// single biggest cost in the old export pipeline (full-frame, per-pixel,
// once per exported frame).
//
// outYuv must be pre-allocated to at least width*height*3/2 bytes.
// If semiPlanar is true, writes NV12 (interleaved UV); otherwise writes
// I420 (planar U then planar V), matching the two color formats
// VideoRenderer.kt's selectCodec/colorFormat logic already handles.
void rgbaToYuv420(
        const uint8_t* rgba,
        int width,
        int height,
        uint8_t* outYuv,
        bool semiPlanar
);
