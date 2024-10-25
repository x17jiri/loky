package com.x17jiri.Loky

interface KeyStore {
	fun takeKeyPair(publicKey: PublicDHKey): DHKeyPair?
}

