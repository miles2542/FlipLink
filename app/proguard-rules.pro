# Shizuku AIDL and UserService reflection entrypoints
-keep class dev.miles.fliplink.data.shizuku.IShellService { *; }
-keep class dev.miles.fliplink.data.shizuku.IShellService$Stub { *; }
-keep class dev.miles.fliplink.data.shizuku.IShellService$Stub$Proxy { *; }
-keep class dev.miles.fliplink.data.shizuku.ShellUserService {
    public <init>(...);
    *;
}
-keep class dev.miles.fliplink.data.shizuku.ShellResult {
    public static final android.os.Parcelable$Creator CREATOR;
    *;
}
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# kotlinx.serialization models and generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class dev.miles.fliplink.domain.model.**$$serializer { *; }
-keepclassmembers class dev.miles.fliplink.domain.model.** {
    *** Companion;
}
-keep,includedescriptorclasses class dev.miles.fliplink.FlipLinkRoute**$$serializer { *; }
-keepclassmembers class dev.miles.fliplink.FlipLinkRoute** {
    *** Companion;
}
