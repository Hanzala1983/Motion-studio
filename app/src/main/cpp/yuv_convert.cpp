#include "yuv_convert.h"
#include <algorithm>

namespace {
inline uint8_t clampByte(int v) {
    return (uint8_t) std::clamp(v, 0, 255);
}
}

void rgbaToYuv420(
        const uint8_t* rgba,
        int width,
        int height,
        uint8_t* outYuv,
        bool semiPlanar
) {
    uint8_t* yPlane = outYuv;
    const int frameSize = width * height;
    uint8_t* uPlane = outYuv + frameSize;
    uint8_t* vPlane = semiPlanar ? (uPlane + 1) : (uPlane + frameSize / 4);
    const int chromaStride = semiPlanar ? 2 : 1;

    for (int j = 0; j < height; j++) {
        for (int i = 0; i < width; i++) {
            const uint8_t* px = rgba + (j * width + i) * 4;
            int r = px[0], g = px[1], b = px[2];

            // BT.601 full-range RGB->YUV, matching the coefficients used by
            // the previous Kotlin bitmapToYuv implementation so exported
            // color matches exactly (no visible shift from the native port).
            int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
            yPlane[j * width + i] = clampByte(y);

            // Chroma is subsampled 2x2 — only compute/write it once per
            // 2x2 block, using the top-left pixel of each block (matches
            // the original implementation's subsampling approach).
            if ((i % 2 == 0) && (j % 2 == 0)) {
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                int chromaIndex = (j / 2) * (width / 2) * chromaStride + (i / 2) * chromaStride;
                uPlane[chromaIndex] = clampByte(u);
                vPlane[chromaIndex] = clampByte(v);
            }
        }
    }
}
