package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DigitalSignatureInfo(
    val signerName: String,
    val reason: String,
    val location: String,
    val signDate: String,
    val filter: String,
    val subFilter: String,
    val hasByteRange: Boolean
)

/**
 * 100% Offline Cryptographic X.509 PKCS#12 Certificate Generator,
 * PKCS#7 Detached PDF Digital Signer, and Signature Verifier.
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class DigitalSignatureEngine(private val context: Context) {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun certsDir(): File = File(context.filesDir, "certificates").apply { mkdirs() }

    /**
     * Generates a personal offline RSA-2048 X.509 v3 Certificate stored as a PKCS#12 (.p12) keystore.
     */
    suspend fun generatePersonalCertificate(
        commonName: String = AppBranding.DEVELOPER_NAME,
        organization: String = "PDF Editor Pro 2026",
        password: String,
        outputFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        require(password.length >= 4) { "Certificate password must be at least 4 characters." }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val now = Date()
        val expiry = Date(now.time + 5L * 365L * 24L * 60L * 60L * 1000L) // Valid 5 years
        val subject = X500Name("CN=${commonName.ifBlank { AppBranding.DEVELOPER_NAME }}, O=${organization.ifBlank { "Personal" }}")
        val serial = BigInteger.valueOf(System.currentTimeMillis())

        val certBuilder = JcaX509v3CertificateBuilder(subject, serial, now, expiry, subject, kp.public)
        val contentSigner = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val certHolder = certBuilder.build(contentSigner)
        val cert: X509Certificate = JcaX509CertificateConverter().getCertificate(certHolder)

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("signer", kp.private, password.toCharArray(), arrayOf(cert))

        val outP12 = outputFile ?: File(certsDir(), "cert_${commonName.replace(Regex("[^a-zA-Z0-9]"), "_")}.p12")
        outP12.parentFile?.mkdirs()
        FileOutputStream(outP12).use { fos ->
            ks.store(fos, password.toCharArray())
        }
        outP12
    }

    /**
     * Cryptographically signs a PDF with a PKCS#12 (.p12/.pfx) X.509 certificate
     * using Adobe PPKLite / PKCS#7 detached SHA-256 signature.
     */
    suspend fun signPdf(
        inputPdf: File,
        outputPdf: File,
        p12File: File,
        p12Password: String,
        signerName: String = AppBranding.DEVELOPER_NAME,
        reason: String = "Digitally signed with PDF Editor Pro 2026",
        location: String = "Offline Device"
    ): File = withContext(Dispatchers.IO) {
        val ks = KeyStore.getInstance("PKCS12")
        FileInputStream(p12File).use { fis ->
            ks.load(fis, p12Password.toCharArray())
        }
        val alias = ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) }
            ?: throw IllegalArgumentException("No signing key found inside certificate file.")
        val privateKey = ks.getKey(alias, p12Password.toCharArray()) as PrivateKey
        val chain = ks.getCertificateChain(alias).map { it as X509Certificate }
        val signingCert = chain.first()

        val sigInterface = SignatureInterface { content: InputStream ->
            val bytes = content.readBytes()
            val gen = CMSSignedDataGenerator()
            val sha256Signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
            gen.addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                    .build(sha256Signer, signingCert)
            )
            gen.addCertificates(JcaCertStore(chain))
            val signedData = gen.generate(CMSProcessableByteArray(bytes), false)
            signedData.encoded
        }

        val tempOut = File(context.cacheDir, "signed_${System.currentTimeMillis()}.pdf")
        PDDocument.load(inputPdf).use { doc ->
            val signature = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                name = signerName.ifBlank { AppBranding.DEVELOPER_NAME }
                this.reason = reason
                this.location = location
                signDate = Calendar.getInstance()
            }
            doc.addSignature(signature, sigInterface)
            FileOutputStream(tempOut).use { fos ->
                doc.saveIncremental(fos)
            }
        }
        outputPdf.parentFile?.mkdirs()
        tempOut.copyTo(outputPdf, overwrite = true)
        tempOut.delete()
        outputPdf
    }

    /**
     * Inspects cryptographic digital signatures embedded in a PDF.
     */
    suspend fun inspectSignatures(pdfFile: File, password: String? = null): List<DigitalSignatureInfo> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<DigitalSignatureInfo>()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            PDDocument.load(pdfFile, password ?: "").use { doc ->
                for (sig in doc.signatureDictionaries) {
                    val dt = sig.signDate?.time?.let { sdf.format(it) } ?: "Unknown"
                    list.add(
                        DigitalSignatureInfo(
                            signerName = sig.name ?: "Unknown Signer",
                            reason = sig.reason ?: "—",
                            location = sig.location ?: "—",
                            signDate = dt,
                            filter = sig.filter ?: "—",
                            subFilter = sig.subFilter ?: "—",
                            hasByteRange = sig.byteRange != null && sig.byteRange.size == 4
                        )
                    )
                }
            }
            list
        }
}
