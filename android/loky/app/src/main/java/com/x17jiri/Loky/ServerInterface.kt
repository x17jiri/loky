package com.x17jiri.Loky

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.URL
import java.util.Calendar
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class LoginOutput(
    @SerializedName("token") val token: Long,
    @SerializedName("key") val key: String,
)

class ServerInterface(context: Context) {
	private val server = "10.0.0.2:9443"
	private var trustManager: X509TrustManager
	private var sslContext: SSLContext
	private var token: Long = 0
	private var key: ByteArray = ByteArray(16)
	private val gson = Gson()
	var step: String = ""

	init {
		val input: InputStream = context.resources.openRawResource(R.raw.ca)
		val serverCA = CertificateFactory
			.getInstance("X.509")
			.generateCertificate(input)

		val keyStoreType = KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(keyStoreType)
        keyStore.load(null, null) // Initialize a new empty keystore
        keyStore.setCertificateEntry("ca", serverCA) // Set the trusted CA

        // Create a TrustManager that trusts the CAs in our KeyStore
		val trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm)
        trustManagerFactory.init(keyStore)
        val trustManagers = trustManagerFactory.getTrustManagers()

		trustManager = trustManagers[0] as X509TrustManager

		sslContext = SSLContext.getInstance("TLS")
		sslContext.init(null, arrayOf(trustManager), SecureRandom())

		HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
		HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
	}
/*
	fun genCert(username: String): Pair<X509Certificate, PrivateKey> {
		// Generate RSA key pair
		val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
		keyPairGenerator.initialize(2048, SecureRandom())
		val keyPair = keyPairGenerator.generateKeyPair()
		val privateKey: PrivateKey = keyPair.private
		val publicKey: PublicKey = keyPair.public

		// Create a self-signed certificate
		val issuer = X500Name("CN=$username's cert")
		val subject = issuer

		// Set the validity period for the certificate
		val calendar = Calendar.getInstance()
		val notBefore = calendar.time
		calendar.add(Calendar.DAY_OF_YEAR, 2000)
		val notAfter = calendar.time

		// Generate a serial number for the certificate
		val serialNumber = BigInteger(160, SecureRandom()).abs()
		val publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.encoded)

		// Create the certificate
		val certGen = X509v3CertificateBuilder(
			issuer,
			serialNumber,
			notBefore,
			notAfter,
			subject,
			publicKeyInfo,
		)

		// Sign the certificate with the private key
		val contentSigner: ContentSigner = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(privateKey)
		val certificate = certGen.build(contentSigner)

		// Convert to X509Certificate
		val certConverter = JcaX509CertificateConverter()
		return Pair(certConverter.getCertificate(certificate), privateKey)
	}
*/
	@OptIn(ExperimentalEncodingApi::class)
	suspend fun login(cred: Credentials): Boolean {
		return withContext(Dispatchers.IO) {
			val name = cred.user
			val passwd = cred.passwd

			// The endpoint is: "https://$server/api/login"
			// Expects a POST request with the following JSON:
			// 	type LoginInput struct {
			// 		Name   string `json:"name"`
			// 		Passwd string `json:"passwd"`
			// 	}
			// The response is a JSON with the following structure:
			// 	type LoginOutput struct {
			//  	Token uint64 `json:"token"`
			//  	Key   []byte `json:"key"`
			// 	}

			val url = "https://$server/api/login"
			val body = """
				{
					"name": "$name",
					"passwd": "$passwd"
				}
			""".trimIndent()

			step = "1"
			val connection = URL(url).openConnection() as HttpsURLConnection
			step = "2"
			connection.requestMethod = "POST"
			step = "3"
			connection.setRequestProperty("Content-Type", "application/json")
			step = "4"
			connection.setRequestProperty("Accept", "application/json")
			step = "5"
			connection.doOutput = true
			step = "6: outputStream = ${connection.outputStream}"

			connection.outputStream.use { os ->
				step = "6.1"
				val input = body.toByteArray(Charsets.UTF_8)
				step = "6.2"
				os.write(input, 0, input.size)
				step = "6.3"
				os.flush()
				step = "6.4"
			}
			step = "7"

			var result: Boolean = false;
			try {
				connection.connect()
				val responseCode = connection.responseCode
				step = "8: responseCode = $responseCode"
				if (responseCode == HttpsURLConnection.HTTP_OK) {
					val response = connection.inputStream.bufferedReader().use { it.readText() }
					step = "9: response = $response"
					val loginOutput = gson.fromJson(response, LoginOutput::class.java)
					step = "10: loginOutput = $loginOutput"

					token = loginOutput.token
					key = Base64.decode(loginOutput.key)
					result = true
				}
			} finally {
				connection.disconnect()
			}

			result
		}
	}
}
