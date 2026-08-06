# Regole consumer del modulo llm (applicate all'R8 dell'app).

# I metodi nativi JNI vanno preservati ovunque.
-keepclasseswithmembernames class * {
    native <methods>;
}

# CRITICO: i simboli JNI sono nominati Java_com_survivalwiki_core_llm_LlamaCppEngine_*.
# Se R8 rinomina la classe/pacchetto, System.loadLibrary trova la lib ma non i simboli.
# Manteniamo intatto nome e metodi della classe che dichiara i native.
-keep class com.survivalwiki.core.llm.LlamaCppEngine { *; }
