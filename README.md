# AI Agent - On-Device GGUF LLM

This is an Android AI Agent built with Jetpack Compose, Hilt, Room, and
llama.cpp for fully local GGUF inference.

## Features
- **On-Device Inference**: Uses llama.cpp and a GGUF model. No network is
  required after the model has been downloaded.
- **Chat Interface**: Earthy, warm design with streaming responses and message history.
- **Persistence**: Conversations are saved locally using Room.
- **Settings**: Pick your own `.gguf` model file and tune inference parameters.

## Setup Instructions

### 1. Download the Model
Download an instruction-tuned model in `.gguf` format. Start with a 1B–4B,
Q4-quantized model so it fits comfortably on a phone. The model must include
the correct chat template in its GGUF metadata.

### 2. Build the Project
1. Open the project in Android Studio (Koala or newer recommended).
2. Sync Gradle.
3. Build and Run on a physical device (recommended) or an emulator with high RAM (8GB+).

### 3. Load the Model in App
1. Open the app.
2. Open the Navigation Drawer -> **Settings**.
3. Tap **Select .gguf file** and pick the model you downloaded.
4. Wait for the model to initialize (check the chat screen for status).

## Design System
- **Background**: #F5EDE0 (Cream)
- **Primary**: #C97B5A (Terracotta)
- **Typography**: Nunito/Inter (System default rounded used for Phase 1)

## Architecture
- MVVM + Repository
- Hilt for Dependency Injection
- Room for local database
- llama.cpp with an Android NDK/JNI bridge for on-device AI

## Native source dependency

`lib/src/main/cpp/llama.cpp` contains a shallow clone of
[`ggml-org/llama.cpp`](https://github.com/ggml-org/llama.cpp). Keep it pinned
to a tested commit before distributing releases, and pull updates deliberately
rather than relying on its moving `master` branch.

This checkout is currently pinned to `bc52a12b38941b0a690ade65fbc5749715224e30`.
