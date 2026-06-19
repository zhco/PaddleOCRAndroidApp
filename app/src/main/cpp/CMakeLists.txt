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

# Find libraries
find_library(log-lib log)
find_library(android-lib android)

# Link only Android log library
# Paddle Lite .so is loaded at runtime via System.loadLibrary, not linked at compile time
target_link_libraries(ocr_jni
    ${log-lib}
    ${android-lib}
)

target_include_directories(ocr_jni PRIVATE ${PADDLE_LITE_INCLUDE_DIR})
