cmake_minimum_required(VERSION 3.22.1)
project(aiagent_native)

set(CMAKE_CXX_STANDARD 17)

# Add llama.cpp as a subdirectory
# Expectation: user runs 'git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp'
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found. Please run: git submodule update --init --recursive")
endif()

add_subdirectory(llama.cpp)

# Define our JNI bridge
add_library(llama_bridge SHARED llama_bridge.cpp)

# Link against llama.cpp libraries
target_link_libraries(llama_bridge llama common)

# Page size support for Android 15
target_link_options(llama_bridge PRIVATE "-Wl,-z,max-page-size=16384")
