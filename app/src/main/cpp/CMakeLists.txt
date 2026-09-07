cmake_minimum_required(VERSION 3.22.1)
project("supernovasnes")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    .
    core
    renderer
    audio
    jni
)

add_library(supernovasnes SHARED
    jni/native-lib.cpp
    core/snes_core.cpp
    core/cpu65816.cpp
    core/snes_ppu.cpp
    core/snes_spc.cpp
    core/snes_memory.cpp
    renderer/gl_renderer.cpp
    audio/audio_engine.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(gles2-lib GLESv2)
find_library(egl-lib EGL)
find_library(sles-lib OpenSLES)

target_link_libraries(supernovasnes
    ${log-lib}
    ${android-lib}
    ${gles2-lib}
    ${egl-lib}
    ${sles-lib}
)
