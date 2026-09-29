# JSch resolves cipher, KEX and MAC implementations by class name from its config strings,
# so R8 cannot see those references and would strip them, breaking every connection in release.
-keep class com.jcraft.jsch.** { *; }
-keep class com.jcraft.jzlib.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.bouncycastle.**

# kotlinx.serialization keeps generated serializers reachable only through synthetic members.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.knotssh.** {
    *** Companion;
}
-keepclasseswithmembers class com.knotssh.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.knotssh.**$$serializer { *; }

# Room entities are instantiated reflectively by generated DAO code.
-keep class com.knotssh.data.local.db.** { *; }

# Google API client is pulled in for the planned Drive backup and uses reflective model binding.
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.**
-dontwarn javax.naming.**
-dontwarn java.lang.management.**

-keepattributes Signature, Exceptions, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
