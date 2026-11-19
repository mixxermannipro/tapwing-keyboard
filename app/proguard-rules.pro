# Tapwing ProGuard / R8 rules
-keep class com.tapwing.keyboard.ime.TapwingInputMethodService { *; }
-keep class com.tapwing.keyboard.settings.SettingsActivity { *; }
-keep class com.tapwing.keyboard.ocr.ScanTextActivity { *; }
-keep class com.tapwing.keyboard.ime.PermissionBridgeActivity { *; }

# Keep ML Kit & CameraX reflection targets
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**

# Keep DataStore & Kotlin coroutines metadata
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
}
