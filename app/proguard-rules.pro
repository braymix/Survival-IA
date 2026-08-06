# R8/ProGuard — regole app. Le regole per librerie native (llama.cpp/onnxruntime)
# verranno aggiunte nelle fasi 3–4 quando i binding JNI saranno presenti.

# Mantieni i metodi nativi (JNI) intatti.
-keepclasseswithmembernames class * {
    native <methods>;
}
