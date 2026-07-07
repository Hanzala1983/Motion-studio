cmake_minimum_required(VERSION 3.22.1)
project("motionstudio_renderer")

# Native rendering engine.
#
# Scope of this first slice: a Kotlin<->C++ JNI bridge plus a CPU-side
# compositor (native_compositor.cpp) that does exactly what
# VideoRenderer.kt's drawShapeLayers/drawMediaLayers/drawTimelineFrame
# do today, but in C++ operating directly on pixel buffers — no OpenGL yet.
# This already removes the biggest bottleneck in the old pipeline
# (bitmapToYuv's per-pixel Kotlin loop) by doing RGBA->YUV420 conversion
# in native code instead, and gives us a real place to grow into GPU
# (OpenGL ES / EGL) compositing next without another rewrite of the JNI
# surface.
add_library(${CMAKE_PROJECT_NAME} SHARED
        native_bridge.cpp
        native_compositor.cpp
        yuv_convert.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE .)

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -O3 -ffast-math)
