package com.x17jiri.Loky

import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class LoginOutput(
	@SerializedName("id") val id: Long,
	@SerializedName("token") val token: String,
)

class ServerInterface(context: Context) {
	private val dataStore = context.dataStore
	private val server = "10.0.0.2:9443"
	private var trustManager: X509TrustManager
	private var sslContext: SSLContext
	private var token: Long = 0
	private var key: ByteArray = ByteArray(16)
	private val gson = Gson()

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

	companion object {
		val __publicKeyKey = stringPreferencesKey("key.public")
		val __privateKeyKey = stringPreferencesKey("key.private")
		val __keyOwnerKey = stringPreferencesKey("key.owner")
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun login(cred: Credentials): Credentials? {
		return withContext(Dispatchers.IO) {
			val name = cred.user
			// Note: the hash is not used for security.
			// It is used so we can limit message size on the server side
			// without limiting the password size.
			val passwd = Base64.encode(Encryptor.hash(cred.passwd))

			var publicKey = ""
			var privateKey = ""
			var keyOwner = ""
			dataStore.data.first().let {
				publicKey = it[__publicKeyKey] ?: ""
				privateKey = it[__privateKeyKey] ?: ""
				keyOwner = it[__keyOwnerKey] ?: ""
			}
			var enc = cred.enc
			if (publicKey.isNotEmpty() && privateKey.isNotEmpty() && keyOwner == name) {
				enc.publicKey = Base64.decode(publicKey)
				enc.privateKey = Base64.decode(privateKey)
			} else {
				enc.generateKeys()
				publicKey = Base64.encode(enc.publicKey!!)
				privateKey = Base64.encode(enc.privateKey!!)
				dataStore.edit {
					it[__publicKeyKey] = publicKey
					it[__privateKeyKey] = privateKey
					it[__keyOwnerKey] = name
				}
			}

			// The endpoint is: "https://$server/api/login"
			// Expects a POST request with the following JSON:
			// 	type LoginInput struct {
			// 		Name   string `json:"name"`
			// 		Passwd []byte `json:"passwd"`
			// 		Key    []byte `json:"key"`
			// 	}
			// The response is a JSON with the following structure:
			// 	type LoginOutput struct {
			// 		Id    uint64 `json:"id"`
			// 		Token []byte `json:"token"`
			// 	}

			val url = "https://$server/api/login"
			val body = """{"name": "$name","passwd": "$passwd","key": "$publicKey"}"""

			val connection = URL(url).openConnection() as HttpsURLConnection
			connection.requestMethod = "POST"
			connection.setRequestProperty("Content-Type", "application/json")
			connection.setRequestProperty("Accept", "application/json")
			connection.doOutput = true

			connection.outputStream.use { os ->
				val input = body.toByteArray(Charsets.UTF_8)
				os.write(input, 0, input.size)
				os.flush()
			}

			var result: Credentials? = null
			try {
				connection.connect()
				val responseCode = connection.responseCode
				if (responseCode == HttpsURLConnection.HTTP_OK) {
					val response = connection.inputStream.bufferedReader().use { it.readText() }
					val loginOutput = gson.fromJson(response, LoginOutput::class.java)
					result = cred.copy(
						id = loginOutput.id,
						token = loginOutput.token,
						enc = enc
					)
				}
			} finally {
				connection.disconnect()
			}

			result
		}
	}

	suspend fun sendLoc(writeCert: Credentials, loc: Location) {
		return withContext(Dispatchers.IO) {
//			val token = writeCert.token
//			val key = writeCert

			// The endpoint is: "https://$server/api/write"
			// Expects a POST request with the following JSON:
			// 	type WriteInputItem struct {
			// 		Group uint8        `json:"group"`
			// 		Loc   EncryptedLoc `json:"loc"`
			// 	}
			// 	type WriteInput struct {
			// 		Token uint64           `json:"token"`
			// 		Key   []byte           `json:"key"`
			// 		Locs  []WriteInputItem `json:"locs"`
			// 	}
			// The response is a HTTP status code

			val url = "https://$server/api/write"

			// TODO
		}
	}
}
