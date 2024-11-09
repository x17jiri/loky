package main

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync/atomic"
)

type UserID struct {
	Id uint64
	Sn uint64
}

func (id UserID) encrypt() EncryptedID {
	encId := EncryptedID{}
	encId.Bytes = make([]byte, 16)
	binary.LittleEndian.PutUint64(encId.Bytes[:8], id.Id)
	binary.LittleEndian.PutUint64(encId.Bytes[8:], id.Sn)
	config.Aes.Encrypt(encId.Bytes, encId.Bytes)
	return encId
}

type EncryptedID struct {
	Bytes []byte
}

func (encId EncryptedID) decrypt() UserID {
	decId := EncryptedID{}
	decId.Bytes = make([]byte, 16)
	config.Aes.Decrypt(decId.Bytes, encId.Bytes)
	id := UserID{
		Id: binary.LittleEndian.Uint64(decId.Bytes[:8]),
		Sn: binary.LittleEndian.Uint64(decId.Bytes[8:]),
	}
	return id
}

func (id EncryptedID) toString() string {
	return base64Encode(id.Bytes)
}

func encryptedIDfromString(encoded string) (EncryptedID, error) {
	decoded, err := base64Decode(encoded)
	if err != nil {
		return EncryptedID{}, err
	}
	if len(decoded) != 16 {
		return EncryptedID{}, fmt.Errorf("invalid EncryptedID length: %d", len(decoded))
	}
	return EncryptedID{Bytes: decoded}, nil
}

func (id EncryptedID) MarshalJSON() ([]byte, error) {
	return json.Marshal(id.toString())
}

func (id *EncryptedID) UnmarshalJSON(data []byte) error {
	var encoded string
	if err := json.Unmarshal(data, &encoded); err != nil {
		return err
	}

	decoded, err := base64Decode(encoded)
	if err != nil {
		return err
	}

	id.Bytes = decoded
	return nil
}

type User struct {
	Id          uint64      `json:"-"` // used instead of username in all operations except login
	Sn          uint64      `json:"sn"`
	EncryptedID EncryptedID `json:"-"`

	Username string      `json:"username"`
	Salt     Base64Bytes `json:"salt"`   // salt for password hashing
	Passwd   Base64Bytes `json:"passwd"` // hashed password

	// TODO - bearer should be verified with signature, we shouldn't store it
	Bearer Bearer `json:"bearer"`

	SigningKey string `json:"sig_key"`    // public key for SigningKey
	MasterKey  string `json:"master_key"` // master public key for diffie-hellman key exchange

	Prekeys     []string                `json:"prekeys"` // public keys for diffie-hellman key exchange
	Inbox       Inbox                   `json:"-"`
	Login       chan LoginRequest       `json:"-"`
	Put         chan PutRequest         `json:"-"`
	Recv        chan RecvRequest        `json:"-"`
	UserInfo    chan UserInfoRequest    `json:"-"`
	FetchPrekey chan FetchPrekeyRequest `json:"-"`
	AddPrekeys  chan AddPrekeysRequest  `json:"-"`
}

func (user *User) needPrekeys() bool {
	return len(user.Prekeys) < PREKEY_COUNT
}

func user_handler(user *User) {
	for {
		select {
		case login := <-user.Login:
			login.Response <- login_synchronized_handler(user, login)
		case put := <-user.Put:
			put.Response <- put_synchronized_handler(user, put)
		case recv := <-user.Recv:
			recv.Response <- recv_synchronized_handler(user)
		case userInfo := <-user.UserInfo:
			userInfo.Response <- userInfo_synchronized_handler(user)
		case fetchPrekey := <-user.FetchPrekey:
			fetchPrekey.Response <- fetchPrekey_synchronized_handler(user, fetchPrekey)
		case addPrekeys := <-user.AddPrekeys:
			addPrekeys.Response <- addPrekeys_synchronized_handler(user, addPrekeys)
		}
	}
}

func usersDir() string {
	return filepath.Join(config.appPath(), "users")
}

func (user *User) userDir() string {
	return filepath.Join(usersDir(), fmt.Sprintf("%019d", user.Id))
}

func (user *User) inboxDir() string {
	return filepath.Join(user.userDir(), "inbox")
}

type Users struct {
	name_map map[string]*User
	id_map   map[uint64]*User
}

func newUsers() *Users {
	return &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[uint64]*User),
	}
}

// The `usersList` is treated as immutable. When we want to change it, we create a new one and
// atomically swap the pointer.
// This way, we don't need any locks for read access. We can just use `usersList.Load()`.
//
// Note that it's the list itself that's immutable, not the users in it.
// Modifying user data still requires synchronization.
var usersList atomic.Pointer[Users]

func (users *Users) userByName(name string) *User {
	user, found := users.name_map[name]
	if !found {
		return nil
	}
	return user
}

func (users *Users) userById(id UserID) *User {
	user, found := users.id_map[id.Id]
	if !found || user.Sn != id.Sn {
		return nil
	}
	return user
}

func (users *Users) shallow_clone() *Users {
	users_clone := &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[uint64]*User),
	}
	for id, user := range users.id_map {
		users_clone.name_map[user.Username] = user
		users_clone.id_map[id] = user
	}
	return users_clone
}

func load_user(id uint64, dir string) (*User, error) {
	json_file := filepath.Join(dir, "user.json")
	bytes, err := os.ReadFile(json_file)
	if err != nil {
		return nil, err
	}

	user := &User{}
	err = json.Unmarshal(bytes, user)
	if err != nil {
		return nil, err
	}

	user.Id = id
	user.EncryptedID = UserID{Id: id, Sn: user.Sn}.encrypt()
	err = user.loadInbox()
	if err != nil {
		return nil, err
	}
	user.Login = make(chan LoginRequest)
	user.Put = make(chan PutRequest)
	user.Recv = make(chan RecvRequest)
	user.UserInfo = make(chan UserInfoRequest)
	user.FetchPrekey = make(chan FetchPrekeyRequest)
	user.AddPrekeys = make(chan AddPrekeysRequest)
	return user, nil
}

func (user *User) save() error {
	jsonData, err := json.MarshalIndent(user, "", "\t")
	if err != nil {
		return err
	}
	jsonData = append(jsonData, '\n')

	json_file := filepath.Join(user.userDir(), "user.json")
	err = os.WriteFile(json_file, jsonData, 0644)
	if err != nil {
		return err
	}

	return nil
}

func load_users() (*Users, error) {
	// Find all subdirs of `dir` whose name can be converted to UserID
	topdir := usersDir()
	dirs, err := os.ReadDir(topdir)
	if err != nil {
		return nil, err
	}

	users := &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[uint64]*User),
	}
	for _, dir := range dirs {
		if !dir.IsDir() {
			Log.i("load_users(): Not a dir: %s", dir.Name())
			continue
		}
		var id uint64
		n, err := fmt.Sscanf(dir.Name(), "%019d", &id)
		if err != nil || n != 1 {
			Log.i("load_users(): Invalid user ID: %s", dir.Name())
			continue
		}
		userDir := filepath.Join(topdir, dir.Name())
		user, err := load_user(id, userDir)
		if err != nil {
			Log.e("load_users(): Error loading user: %s", err.Error())
			continue
		}
		users.name_map[user.Username] = user
		users.id_map[id] = user

		encID := UserID{Id: id, Sn: user.Sn}.encrypt().toString()
		Log.i("User loaded: %s, ID: %019d, encID: %s", user.Username, id, encID)
	}
	return users, nil
}

func addUser(users *Users, username string, passwd []byte) (*Users, error) {
	_, found := users.name_map[username]
	if found {
		return nil, fmt.Errorf("user name %s already exists", username)
	}

	salt, err := randBytes(16)
	if err != nil {
		return nil, err
	}

	hashed_passwd := __hash_passwd(passwd, salt)

	id, err := config.genId()
	if err != nil {
		return nil, err
	}

	user := &User{
		Id:          id.Id,
		Sn:          id.Sn,
		EncryptedID: id.encrypt(),

		Username: username,
		Salt:     salt,
		Passwd:   hashed_passwd,

		Bearer: "",

		SigningKey: "",
		MasterKey:  "",

		Prekeys:     make([]string, 0),
		Inbox:       Inbox{},
		Login:       make(chan LoginRequest),
		Put:         make(chan PutRequest),
		Recv:        make(chan RecvRequest),
		UserInfo:    make(chan UserInfoRequest),
		FetchPrekey: make(chan FetchPrekeyRequest),
		AddPrekeys:  make(chan AddPrekeysRequest),
	}
	user.Inbox = newInbox(user)

	// mkdir
	err = os.MkdirAll(user.inboxDir(), 0755)
	if err != nil {
		return nil, err
	}

	err = user.save()
	if err != nil {
		return nil, err
	}

	newUsers := users.shallow_clone()
	newUsers.name_map[username] = user
	newUsers.id_map[id.Id] = user

	return newUsers, nil
}
