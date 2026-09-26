@echo off
"D:\\programs\\Android\\sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  -C ^
  "D:\\code\\aiagent\\lib\\.cxx\\Release\\3r5t2f1r\\arm64-v8a" ^
  ggml ^
  ggml-base ^
  ggml-cpu-android_armv8.0_1 ^
  ggml-cpu-android_armv8.2_1 ^
  ggml-cpu-android_armv8.2_2 ^
  ggml-cpu-android_armv8.6_1 ^
  ggml-cpu-android_armv9.0_1 ^
  ggml-cpu-android_armv9.2_1 ^
  ggml-cpu-android_armv9.2_2 ^
  llama ^
  llama-common ^
  llama_bridge
