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

# Find system libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
# Note: We do NOT link paddle_light_api_shared at build time.
# The prebuilt .so has symbol table issues with NDK r25+ lld linker.
# Instead, we load it at runtime via System.loadLibrary() in Java/Kotlin.
# We add -Wl,--unresolved-symbols=ignore-all to allow undefined Paddle Lite
# symbols in our shared library; they will be resolved at runtime when
# libpaddle_light_api_shared.so is loaded into the same process.
target_link_libraries(ocr_jni
    ${log-lib}
    ${android-lib}
)

# Allow undefined symbols - they will be resolved at runtime
# when Paddle Lite shared library is loaded via System.loadLibrary
target_link_options(ocr_jni PRIVATE -Wl,--unresolved-symbols=ignore-all)

target_include_directories(ocr_jni PRIVATE ${PADDLE_LITE_INCLUDE_DIR})
