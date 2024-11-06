package main

import (
	"bytes"
	"crypto/sha256"

	"golang.org/x/crypto/pbkdf2"
)

func __hash_passwd(password []byte, salt []byte) []byte {
	return pbkdf2.Key(password, salt, 16, 32, sha256.New)
}

func (user *User) check_passwd(passwd []byte) bool {
	hashed_passwd := __hash_passwd(passwd, user.Salt)
	return bytes.Equal(hashed_passwd, user.Passwd)
}
