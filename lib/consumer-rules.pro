-keep class com.example.llama.LlamaEngine {
    native <methods>;
}

-keepclassmembers class com.example.llama.LlamaEngine$LlamaCallback {
    void onToken(java.lang.String);
    void onFinished(java.lang.String);
}
