# Keep Gson models
-keep class com.ibrahimjaber.pdfpro.data.** { *; }

# Keep PDFBox-Android core classes
-keep class com.tom_roush.pdfbox.** { *; }

# Keep only BouncyCastle crypto providers needed for offline PDF AES encryption
# (Allows R8 to strip unused bcpkix TLS/EST TrustManager classes for Google Play Security Scan)
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.crypto.** { *; }

-dontwarn org.bouncycastle.**
-dontwarn com.tom_roush.pdfbox.**
