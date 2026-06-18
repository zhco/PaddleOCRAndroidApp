cmake_minimum_required(VERSION 3.10.2)
project("paddleocr_native")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Paddle Lite library path
set(PADDLE_LITE_DIR "${CMAKE_SOURCE_DIR}/../../../libs/${ANDROID_ABI}")

# Include directories
include_directories(
    ${PADDLE_LITE_DIR}/include
    ${CMAKE_SOURCE_DIR}/include
)

# Source files
file(GLOB_RECURSE NATIVE_SRCS
    ${CMAKE_SOURCE_DIR}/*.cpp
)

# Create shared library
add_library(ocr_native SHARED ${NATIVE_SRCS})

# Link libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# Paddle Lite shared library
add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION ${PADDLE_LITE_DIR}/libpaddle_light_api_shared.so
)

# OpenCV (optional, for image preprocessing)
# add_library(opencv_core SHARED IMPORTED)
# set_target_properties(opencv_core PROPERTIES IMPORTED_LOCATION ${PADDLE_LITE_DIR}/libopencv_core.so)

target_link_libraries(ocr_native
    paddle_light_api_shared
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)
