package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"strings"
	"time"
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

func randBytes(count int) ([]byte, error) {
	bytes := make([]byte, count)
	_, err := rand.Read(bytes)
	if err != nil {
		return nil, err
	}
	return bytes, nil
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

func makeBearer(id EncryptedID) (Bearer, error) {
	token, err := randBytes(16)
	if err != nil {
		return "", err
	}

	bearer := fmt.Sprintf("%s.%s", id.toString(), base64Encode(token))

	return Bearer(bearer), nil
}

func (bearer Bearer) toUserID() (UserID, error) {
	parts := strings.Split(string(bearer), ".")
	if len(parts) != 2 {
		return UserID{}, fmt.Errorf("invalid bearer string: %s", bearer)
	}

	encId, err := encryptedIDfromString(parts[0])
	if err != nil {
		return UserID{}, err
	}

	return encId.decrypt(), nil
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
