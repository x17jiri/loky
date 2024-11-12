package main

import (
	"crypto/aes"
	"crypto/cipher"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"sync"
	"time"
)

// 2000-01-01 00:00:00 UTC
var referenceTime = time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC)

const MINUTE int64 = 60
const HOUR int64 = 60 * MINUTE

const SWITCH_INBOX_SEC int64 = 30 * MINUTE
const MSG_EXPIRE_SEC int64 = 2 * HOUR

const PREKEY_COUNT = 100
const PREKEY_MAX_COUNT = 2 * PREKEY_COUNT

var config Config

type UsedInvitation struct {
	Code string `json:"code"`
	By   string `json:"by"`
}

type Config struct {
	AesKey          AesKey           `json:"aes_key"`
	LastId          uint64           `json:"last_id"`
	Filename        string           `json:"-"`
	Aes             cipher.Block     `json:"-"`
	Invitations     []string         `json:"invitations"`
	UsedInvitations []UsedInvitation `json:"used_invitations"`
}

var GlobalLock sync.Mutex = sync.Mutex{}

func (config *Config) appPath() string {
	return "."
}

type AesKey struct {
	Key []byte
}

func newConfig(fn string) (Config, error) {
	aesKey, err := newAesKey()
	if err != nil {
		return Config{}, err
	}

	aes, err := aes.NewCipher(aesKey.Key)
	if err != nil {
		return Config{}, err
	}
	if aes.BlockSize() != 16 {
		return Config{}, fmt.Errorf("invalid block size: %d", aes.BlockSize())
	}

	idBytes, err := randBytes(8)
	if err != nil {
		return Config{}, err
	}
	id := binary.LittleEndian.Uint64(idBytes)

	cfg := Config{
		AesKey:   aesKey,
		LastId:   id,
		Filename: fn,
		Aes:      aes,
	}

	err = cfg.save()
	if err != nil {
		return Config{}, err
	}

	return cfg, nil
}

func (cfg *Config) save() error {
	jsonData, err := json.MarshalIndent(cfg, "", "\t")
	if err != nil {
		return err
	}
	jsonData = append(jsonData, '\n')

	err = os.WriteFile(cfg.Filename, jsonData, 0644)
	if err != nil {
		return err
	}

	return nil
}

func (cfg *Config) genId() (UserID, error) {
	id := cfg.LastId
	id++
	cfg.LastId = id
	err := cfg.save()
	if err != nil {
		return UserID{}, fmt.Errorf("error saving config: %s", err)
	}

	snBytes, err := randBytes(8)
	if err != nil {
		return UserID{}, fmt.Errorf("error generating sn: %s", err)
	}
	sn := binary.LittleEndian.Uint64(snBytes)

	return UserID{
		Id: id,
		Sn: sn,
	}, nil
}

func loadConfig(fn string) (Config, error) {
	bytes, err := os.ReadFile(fn)
	if err != nil {
		return Config{}, fmt.Errorf("error reading config: %s", err)
	}

	cfg := Config{}
	err = json.Unmarshal(bytes, &cfg)
	if err != nil {
		return Config{}, fmt.Errorf("error parsing config: %s", err)
	}
	cfg.Filename = fn

	cfg.Aes, err = aes.NewCipher(cfg.AesKey.Key)
	if err != nil {
		return Config{}, fmt.Errorf("error creating cipher: %s", err)
	}
	if cfg.Aes.BlockSize() != 16 {
		return Config{}, fmt.Errorf("invalid block size: %d", cfg.Aes.BlockSize())
	}

	return cfg, nil
}

func newAesKey() (AesKey, error) {
	key, err := randBytes(32)
	if err != nil {
		return AesKey{}, err
	}

	return AesKey{Key: key}, nil
}

func (ak *AesKey) MarshalJSON() ([]byte, error) {
	return json.Marshal(base64Encode(ak.Key))
}

func (ak *AesKey) UnmarshalJSON(data []byte) error {
	var encoded string
	if err := json.Unmarshal(data, &encoded); err != nil {
		return err
	}

	decoded, err := base64Decode(encoded)
	if err != nil {
		return err
	}

	if len(decoded) != 32 {
		return fmt.Errorf("invalid key length: %d", len(decoded))
	}

	ak.Key = decoded
	return nil
}
