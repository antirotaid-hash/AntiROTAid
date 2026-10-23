# Chaquopy + Compose keep rules. The Python engine is interpreted at runtime, so
# only the thin Kotlin bridge needs to survive shrinking. Keep Chaquopy runtime.
-keep class com.chaquo.python.** { *; }
-keepclassmembers class com.mindlift.bridge.** { *; }
