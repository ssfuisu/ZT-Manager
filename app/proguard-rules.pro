# Native 7-Zip engine calls back into Java through JNI.
-keep class net.sf.sevenzipjbinding.** { *; }
-keep interface net.sf.sevenzipjbinding.** { *; }
-keep class * implements net.sf.sevenzipjbinding.IArchiveExtractCallback { *; }
-keep class * implements net.sf.sevenzipjbinding.IArchiveOpenCallback { *; }
-keep class * implements net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback { *; }
-keep class * implements net.sf.sevenzipjbinding.ICryptoGetTextPassword { *; }
-keep class * implements net.sf.sevenzipjbinding.ISequentialOutStream { *; }
-keep class * implements net.sf.sevenzipjbinding.IInStream { *; }

-keep class net.lingala.zip4j.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }

# Optional dependencies referenced by commons-compress / commons-lang3 / commons-io.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn java.beans.**
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.codec.**
-dontwarn net.sf.sevenzipjbinding.**
-ignorewarnings
