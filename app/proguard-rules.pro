# Keep Gson models
-keep class com.ibrahimjaber.pdfpro.data.** { *; }

# Keep PDFBox-Android classes & BouncyCastle crypto providers
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn com.tom_roush.pdfbox.**
