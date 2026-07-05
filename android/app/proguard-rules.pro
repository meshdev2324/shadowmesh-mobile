# UniFFI Specific Rules
-keep class uniffi.** { *; }
-keepclassmembers class uniffi.** { *; }

# WireGuard Specific Rules
-keep class com.wireguard.** { *; }
-keepclassmembers class com.wireguard.** { *; }

# JNA Specific Rules
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }

# Keep all Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
