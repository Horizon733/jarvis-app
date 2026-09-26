cmake_minimum_required(VERSION 3.22.1)
project(llama_lib)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is deliberately kept as a source checkout: Gradle/NDK builds it
# together with the JNI bridge for the target Android ABI.
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp is missing. Clone ggml-org/llama.cpp into ${LLAMA_SRC}")
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH ARM)
    set(GGML_CPU_KLEIDIAI ON)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(GGML_SYSTEM_ARCH x86)
    set(GGML_CPU_KLEIDIAI OFF)
else()
    message(FATAL_ERROR "Unsupported Android ABI: ${ANDROID_ABI}")
endif()

add_subdirectory(${LLAMA_SRC} build-llama)

add_library(llama_bridge SHARED cpp/ai_chat.cpp)
target_include_directories(llama_bridge PRIVATE
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src)
target_link_libraries(llama_bridge PRIVATE llama llama-common android log)
target_link_options(llama_bridge PRIVATE "-Wl,-z,max-page-size=16384")
