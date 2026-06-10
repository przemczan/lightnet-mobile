# Android NDK build: wraps the firmware's portable animation core (LightnetCore) + its C ABI in a
# JNI shared library (liblightnet_anim.so).
#
# LIGHTNET_FIRMWARE_DIR is passed from Gradle (resolved: -PlightnetFirmwareDir, else the
# third_party/lightnet-firmware submodule, else a sibling ../lightnet-firmware checkout).
# We reuse the firmware's lib/Lightnet/Core/CApi/CMakeLists (the `anim_core` static lib) via
# add_subdirectory, with its host smoke test disabled for the cross-build.

cmake_minimum_required(VERSION 3.22.1)
project(lightnet_anim CXX)

if(NOT DEFINED LIGHTNET_FIRMWARE_DIR)
    message(FATAL_ERROR "LIGHTNET_FIRMWARE_DIR not set (expected from Gradle).")
endif()

set(ANIM_CORE_C_DIR "${LIGHTNET_FIRMWARE_DIR}/lib/Lightnet/Core/CApi")
if(NOT EXISTS "${ANIM_CORE_C_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "anim-core-c not found at '${ANIM_CORE_C_DIR}'. Check LIGHTNET_FIRMWARE_DIR.")
endif()

set(ANIM_CORE_BUILD_TESTS OFF CACHE BOOL "" FORCE)  # host smoke exe is not for Android
add_subdirectory("${ANIM_CORE_C_DIR}" anim_core_build)

add_library(lightnet_anim SHARED jni_anim.cpp)
# anim_core exposes its include dirs PUBLIC-ly (anim_core_c.h + Core/Anim headers).
target_link_libraries(lightnet_anim anim_core)

# 16 KB page size compatibility (required for newer Android devices/emulators).
target_link_options(lightnet_anim PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(lightnet_anim ${log-lib})
