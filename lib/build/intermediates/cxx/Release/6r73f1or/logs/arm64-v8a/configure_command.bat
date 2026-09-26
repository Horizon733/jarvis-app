@echo off
"D:\\programs\\Android\\sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HD:\\code\\aiagent\\lib\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=26" ^
  "-DANDROID_PLATFORM=android-26" ^
  "-DANDROID_ABI=arm64-v8a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a" ^
  "-DANDROID_NDK=D:\\programs\\Android\\sdk\\ndk\\26.1.10909125" ^
  "-DCMAKE_ANDROID_NDK=D:\\programs\\Android\\sdk\\ndk\\26.1.10909125" ^
  "-DCMAKE_TOOLCHAIN_FILE=D:\\programs\\Android\\sdk\\ndk\\26.1.10909125\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=D:\\programs\\Android\\sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_CXX_FLAGS=-std=c++17 -fexceptions -frtti" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=D:\\code\\aiagent\\lib\\build\\intermediates\\cxx\\Release\\6r73f1or\\obj\\arm64-v8a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=D:\\code\\aiagent\\lib\\build\\intermediates\\cxx\\Release\\6r73f1or\\obj\\arm64-v8a" ^
  "-BD:\\code\\aiagent\\lib\\.cxx\\Release\\6r73f1or\\arm64-v8a" ^
  -GNinja ^
  "-DCMAKE_BUILD_TYPE=Release" ^
  "-DBUILD_SHARED_LIBS=ON" ^
  "-DLLAMA_BUILD_EXAMPLES=OFF" ^
  "-DLLAMA_BUILD_TESTS=OFF" ^
  "-DLLAMA_BUILD_SERVER=OFF" ^
  "-DLLAMA_BUILD_COMMON=ON" ^
  "-DLLAMA_OPENSSL=OFF" ^
  "-DGGML_NATIVE=OFF" ^
  "-DGGML_BACKEND_DL=OFF" ^
  "-DGGML_LLAMAFILE=OFF"
