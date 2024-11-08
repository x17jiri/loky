package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync/atomic"
)

type UserID uint64

func (id UserID) toString() string {
	part1 := (id >> 48) & 0xffff
	part2 := (id >> 32) & 0xffff
	part3 := (id >> 16) & 0xffff
	part4 := id & 0xffff
	return fmt.Sprintf("%04x_%04x_%04x_%04x", part1, part2, part3, part4)
}

func userIDFromString(s string) (UserID, error) {
	parts := strings.Split(s, "_")
	if len(parts) != 4 {
		return 0, fmt.Errorf("invalid UserID string: %s", s)
	}

	var id uint64 = 0
	for _, part := range parts {
		if len(part) != 4 {
			return 0, fmt.Errorf("invalid UserID string: %s", s)
		}

		val, err := strconv.ParseUint(part, 16, 16)
		if err != nil {
			return 0, err
		}

		id = (id << 16) | val
	}
	return UserID(id), nil
}

func (id UserID) MarshalJSON() ([]byte, error) {
	return json.Marshal(id.toString())
}

func (id *UserID) UnmarshalJSON(data []byte) error {
	var idStr string
	if err := json.Unmarshal(data, &idStr); err != nil {
		return err
	}

	parsedID, err := userIDFromString(idStr)
	if err != nil {
		return err
	}

	*id = parsedID
	return nil
}

type User struct {
	Id UserID `json:"-"` // used instead of username in all operations except login

	Username string      `json:"username"`
	Salt     Base64Bytes `json:"salt"`   // salt for password hashing
	Passwd   Base64Bytes `json:"passwd"` // hashed password

	// TODO - bearer should be verified with signature, we shouldn't store it
	Bearer Bearer `json:"bearer"`

	Key string `json:"key"` // public key for signing

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
	return filepath.Join(appPath(), "users")
}

func (user *User) userDir() string {
	return filepath.Join(usersDir(), user.Id.toString())
}

func (user *User) inboxDir() string {
	return filepath.Join(user.userDir(), "inbox")
}

type Users struct {
	name_map map[string]*User
	id_map   map[UserID]*User
}

func newUsers() *Users {
	return &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[UserID]*User),
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
	user, found := users.id_map[id]
	if !found {
		return nil
	}
	return user
}

func (users *Users) shallow_clone() *Users {
	users_clone := &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[UserID]*User),
	}
	for id, user := range users.id_map {
		users_clone.name_map[user.Username] = user
		users_clone.id_map[id] = user
	}
	return users_clone
}

func load_user(id UserID, dir string) (*User, error) {
	json_file := filepath.Join(dir, "user.json")
	bytes, err := read_file(json_file)
	if err != nil {
		return nil, err
	}

	user := &User{}
	err = json.Unmarshal(bytes, user)
	if err != nil {
		return nil, err
	}

	user.Id = id
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

func (user *User) save_user() error {
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
		id_map:   make(map[UserID]*User),
	}
	for _, dir := range dirs {
		if !dir.IsDir() {
			fmt.Println("load_users(): Not a dir: ", dir.Name())
			continue
		}
		id, err := userIDFromString(dir.Name())
		if err != nil {
			fmt.Println("load_users(): Invalid user ID: ", dir.Name())
			continue
		}
		if dir.Name() != id.toString() {
			// This could happen if our user ID encoding uses lower case letters for hex digits
			// and someone names the dir with upper case letters.
			// On case-sensitive filesystems, this would be a problem.
			fmt.Println(
				"load_users(): Invalid user ID encoding: '" +
					dir.Name() + "', expected: '" + id.toString() + "'",
			)
			continue
		}
		userDir := filepath.Join(topdir, dir.Name())
		user, err := load_user(id, userDir)
		if err != nil {
			fmt.Println("load_users(): Error loading user: ", err)
			continue
		}
		users.name_map[user.Username] = user
		users.id_map[id] = user

		fmt.Println("User loaded: ", user.Username, ", ID: ", id.toString())
	}
	return users, nil
}

func __gen_unique_id(users *Users) (UserID, error) {
	var id UserID
	var err error
	var i int
	for i = 0; i < 10; i++ {
		id, err = __gen_id()
		if err != nil {
			return 0, err
		}
		_, found := users.id_map[id]
		if !found {
			break
		}
	}
	if i == 10 {
		for {
			id++
			_, found := users.id_map[id]
			if !found {
				break
			}
		}
	}
	return id, nil
}

func addUser(users *Users, username string, passwd []byte) (*Users, error) {
	_, found := users.name_map[username]
	if found {
		return nil, fmt.Errorf("user name %s already exists", username)
	}

	salt, err := __gen_salt(16)
	if err != nil {
		return nil, err
	}

	hashed_passwd := __hash_passwd(passwd, salt)

	id, err := __gen_unique_id(users)
	if err != nil {
		return nil, err
	}

	user := &User{
		Username: username,
		Id:       id,
		Salt:     salt,
		Passwd:   hashed_passwd,

		Bearer: "",

		Key: "",

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

	err = user.save_user()
	if err != nil {
		return nil, err
	}

	newUsers := users.shallow_clone()
	newUsers.name_map[username] = user
	newUsers.id_map[id] = user

	return newUsers, nil
}
