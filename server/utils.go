package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"io"
	"os"
	"path/filepath"

	"golang.org/x/crypto/pbkdf2"
)

func read_file(filename string) ([]byte, error) {
	file, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	return io.ReadAll(file)
}

func __gen_salt(count int) ([]byte, error) {
	salt := make([]byte, count)
	_, err := rand.Read(salt)
	if err != nil {
		return nil, err
	}
	return salt, nil
}

func __gen_id() (uint64, error) {
	id := make([]byte, 8)
	_, err := rand.Read(id)
	if err != nil {
		return 0, err
	}
	return binary.LittleEndian.Uint64(id), nil
}

func __hash_passwd(password []byte, salt []byte) []byte {
	return pbkdf2.Key(password, salt, 16, 32, sha256.New)
}

func configPath(filename string) string {
	return filepath.Join(".", filename)
}

func hash(input string) []byte {
	// Create a new SHA-256 hash object
	hasher := sha256.New()

	// Write the input string as bytes (UTF-8 encoding by default in Go)
	hasher.Write([]byte(input))

	// Get the computed hash as a byte slice
	return hasher.Sum(nil)
}
