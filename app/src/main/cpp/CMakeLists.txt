cmake_minimum_required(VERSION 3.10.2)
project("ocr_jni")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Paddle Lite include directory
set(PADDLE_LITE_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/include)

# Source files
file(GLOB NATIVE_SRCS ${CMAKE_SOURCE_DIR}/*.cpp)

# Create shared library
add_library(ocr_jni SHARED ${NATIVE_SRCS})

# Add Paddle Lite prebuilt shared library
# The .so is bundled in jniLibs and loaded at runtime by System.loadLibrary
# But we need to link against it at build time so our JNI code can reference
# Paddle Lite C++ symbols.
add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaddle_light_api_shared.so
)

# Find system libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
# paddle_light_api_shared must be linked so ocr_jni.so has the symbols resolved.
target_link_libraries(ocr_jni
    paddle_light_api_shared
    ${log-lib}
    ${android-lib}
)

target_include_directories(ocr_jni PRIVATE ${PADDLE_LITE_INCLUDE_DIR})
