cmake_minimum_required(VERSION 3.10.2)
project("ocr_jni")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Paddle Lite library path
set(PADDLE_LITE_DIR "${CMAKE_SOURCE_DIR}/../../../libs/${ANDROID_ABI}")

# Include directories
include_directories(
    ${PADDLE_LITE_DIR}/include
)

# Source files
file(GLOB_RECURSE NATIVE_SRCS
    ${CMAKE_SOURCE_DIR}/*.cpp
)

# Create shared library
add_library(ocr_jni SHARED ${NATIVE_SRCS})

# Link libraries
find_library(log-lib log)
find_library(android-lib android)

# Paddle Lite shared library
add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION ${PADDLE_LITE_DIR}/libpaddle_light_api_shared.so
)

target_link_libraries(ocr_jni
    paddle_light_api_shared
    ${log-lib}
    ${android-lib}
)
