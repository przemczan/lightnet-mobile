# Android NDK build: wraps the firmware's portable animation core (LightnetCore) + scene engine and
# their C ABIs in a JNI shared library (liblightnet_anim.so).
#
# LIGHTNET_FIRMWARE_DIR is passed from Gradle (resolved: -PlightnetFirmwareDir, else the
# third_party/lightnet-firmware submodule, else a sibling ../lightnet-firmware checkout).
# We reuse the firmware's lib/Lightnet/Core/CApi/CMakeLists (the `panel_core` AND `controller_core` static
# libs) via add_subdirectory, with its host smoke tests disabled for the cross-build.

cmake_minimum_required(VERSION 3.22.1)
project(lightnet_anim CXX)

if(NOT DEFINED LIGHTNET_FIRMWARE_DIR)
    message(FATAL_ERROR "LIGHTNET_FIRMWARE_DIR not set (expected from Gradle).")
endif()

set(ANIM_CORE_C_DIR "${LIGHTNET_FIRMWARE_DIR}/lib/Lightnet/Core/CApi")
if(NOT EXISTS "${ANIM_CORE_C_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "anim-core-c not found at '${ANIM_CORE_C_DIR}'. Check LIGHTNET_FIRMWARE_DIR.")
endif()

set(ANIM_CORE_BUILD_TESTS OFF CACHE BOOL "" FORCE)  # host smoke exes are not for Android
add_subdirectory("${ANIM_CORE_C_DIR}" panel_core_build)  # defines panel_core + controller_core

add_library(lightnet_anim SHARED jni_anim.cpp jni_scene.cpp)
# panel_core / controller_core expose their include dirs PUBLIC-ly (the C ABI headers + Core/Panel, Core/Controller/Scene).
target_link_libraries(lightnet_anim panel_core controller_core)

# 16 KB page size compatibility (required for newer Android devices/emulators).
target_link_options(lightnet_anim PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(lightnet_anim ${log-lib})
