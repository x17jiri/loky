package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strings"
	"time"
	"unsafe"
)

type TimeRange struct {
	From int64 `json:"from"`
	To   int64 `json:"to"`
}

func timeRange(from int64, len int64) TimeRange {
	return TimeRange{From: from, To: from + len}
}

func (tr TimeRange) contains(t int64) bool {
	return tr.From <= t && t < tr.To
}

func (tr TimeRange) overlaps(other TimeRange) bool {
	return tr.From < other.To && other.From < tr.To
}

func monotonicSeconds() int64 {
	return int64(time.Since(referenceTime).Seconds())
}

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

func __gen_id() (UserID, error) {
	var idBytes [8]byte = [unsafe.Sizeof(UserID(0))]byte{}
	_, err := rand.Read(idBytes[:])
	if err != nil {
		return 0, err
	}
	return UserID(binary.LittleEndian.Uint64(idBytes[:])), nil
}

func hash(input string) []byte {
	// Create a new SHA-256 hash object
	hasher := sha256.New()

	// Write the input string as bytes (UTF-8 encoding by default in Go)
	hasher.Write([]byte(input))

	// Get the computed hash as a byte slice
	return hasher.Sum(nil)
}

func base64Encode(input []byte) string {
	return base64.RawURLEncoding.EncodeToString(input)
}

func base64Decode(input string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(input)
}

type Base64Bytes []byte

func (b Base64Bytes) MarshalJSON() ([]byte, error) {
	encoded := base64Encode(b)
	return json.Marshal(encoded)
}

func (b *Base64Bytes) UnmarshalJSON(data []byte) error {
	var encoded string
	if err := json.Unmarshal(data, &encoded); err != nil {
		return err
	}

	decoded, err := base64Decode(encoded)
	if err != nil {
		return err
	}

	*b = Base64Bytes(decoded)
	return nil
}

type Bearer string

func makeBearer(id UserID) (Bearer, error) {
	token := [16]byte{}
	_, err := rand.Read(token[:])
	if err != nil {
		return "", err
	}

	bearer := fmt.Sprintf("%s.%s", id.toString(), base64Encode(token[:]))

	return Bearer(bearer), nil
}

func (bearer Bearer) toUserID() (UserID, error) {
	parts := strings.Split(string(bearer), ".")
	if len(parts) != 2 {
		return 0, fmt.Errorf("invalid bearer string: %s", bearer)
	}

	return userIDFromString(parts[0])
}

func find_first[T any](slice []T, predicate func(T) bool) int {
	for i, v := range slice {
		if predicate(v) {
			return i
		}
	}
	return len(slice) // Return length if no item satisfies the predicate
}

func shift[T any](slice []T, n int) []T {
	L := len(slice)
	if n > L {
		n = L
	}

	if n <= 0 {
		return slice
	}

	copy(slice, slice[n:])
	var empty T
	for i := L - n; i < L; i++ {
		slice[i] = empty
	}
	return slice[:L-n]
}
