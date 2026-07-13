# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
# libpd / Pure Data JNI callback rules
# libpd's native code invokes these methods by reflection; R8 must not
# rename or remove them, otherwise PdBase.setReceiver() throws
# NoSuchMethodError at runtime.

# Keep the PdReceiver interface and all its callback methods exactly as named.
-keep interface org.puredata.core.PdReceiver {
    <methods>;
}

# Keep every implementation of PdReceiver (including anonymous inner classes)
# and all of its callback methods.
-keep class * implements org.puredata.core.PdReceiver {
    <methods>;
}

# Keep PdBase native methods and the public static API used by the app.
-keep class org.puredata.core.PdBase {
    native <methods>;
    public static *;
}

# Keep libpd utility classes used for asset/patch handling.
-keep class org.puredata.core.utils.** { *; }

# Keep libpd Android audio classes.
-keep class org.puredata.android.io.** { *; }

#-renamesourcefileattribute SourceFile