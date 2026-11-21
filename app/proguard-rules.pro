# Keep Gson models
-keep class com.ibrahimjaber.pdfpro.data.** { *; }

# Keep PDFBox-Android & Tesseract4Android JNI classes
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.googlecode.tesseract.** { *; }
-keep class com.googlecode.leptonica.** { *; }

# Keep BouncyCastle crypto & CMS classes needed for offline AES encryption and PKCS#7/X.509 PDF signing
# (Allows R8 to strip unused bcpkix TLS/EST TrustManager classes for Google Play Security Scan)
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.cms.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-keep class org.bouncycastle.asn1.** { *; }

-dontwarn org.bouncycastle.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.googlecode.tesseract.**
