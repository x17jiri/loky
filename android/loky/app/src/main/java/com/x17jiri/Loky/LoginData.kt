package com.x17jiri.Loky

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class LoginData(dataStore: DataStore<Preferences>) {
	var __dataStore = dataStore
	var user: String = ""
	var passwd: String = ""
	var cert: X509Certificate? = null

	val __userKey: Preferences.Key<String> = stringPreferencesKey("login.user")
	val __passwdKey: Preferences.Key<String> = stringPreferencesKey("login.passwd")
	val __certKey: Preferences.Key<String> = stringPreferencesKey("login.cert")

	init {
		runBlocking {
			var certStr: String = ""
			__dataStore.data.first().let {
				user = it[__userKey] ?: ""
				passwd = it[__passwdKey] ?: ""
				certStr = it[__certKey] ?: ""
			}

			// certStr is either empty or a base64 encoded certificate
			if (certStr.isNotEmpty()) {
				val bytes = Base64.decode(certStr)
				val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
				cert = certificateFactory
					.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
			}
		}
	}

	fun storeCredentials(user: String, passwd: String) {
		this.user = user
		this.passwd = passwd
		runBlocking {
			__dataStore.edit {
				it[__userKey] = user
				it[__passwdKey] = passwd
			}
		}
	}
}

