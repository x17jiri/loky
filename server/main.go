package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"sort"
	"sync/atomic"
	"time"
)

type Message struct {
	From      int64
	Timestamp time.Time
	Data      string
}

type User struct {
	Name   string `json:"name"`
	Salt   []byte `json:"salt"`   // salt for password hashing
	Passwd []byte `json:"passwd"` // hashed password

	Id    int64  `json:"id"`    // used instead of name in all operations except login
	Token []byte `json:"token"` // used instead of password in all operations except login

	Key     []byte `json:"key"`     // public key for encryption
	KeyHash []byte `json:"keyHash"` // hash of the public key

	Inbox    []Message            `json:"-"`
	Login    chan LoginRequest    `json:"-"`
	Send     chan SendRequest     `json:"-"`
	Recv     chan RecvRequest     `json:"-"`
	UserInfo chan UserInfoRequest `json:"-"`
}

type Users struct {
	name_map map[string]*User
	id_map   map[int64]*User
	filename string
}

func (users *Users) userByName(name string) *User {
	user, found := users.name_map[name]
	if !found {
		return nil
	}
	return user
}

func (users *Users) userById(id int64) *User {
	user, found := users.id_map[id]
	if !found {
		return nil
	}
	return user
}

func (users *Users) clone() *Users {
	users_clone := &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[int64]*User),
		filename: users.filename,
	}
	for id, user := range users.id_map {
		user_copy := *user
		users_clone.name_map[user.Name] = &user_copy
		users_clone.id_map[id] = &user_copy
	}
	return users_clone
}

func load_users(filename string) (*Users, error) {
	// check if file exists
	if _, err := os.Stat(filename); os.IsNotExist(err) {
		return &Users{
			name_map: make(map[string]*User),
			id_map:   make(map[int64]*User),
		}, nil
	}

	bytes, err := read_file(filename)
	if err != nil {
		return nil, err
	}

	var array []*User
	err = json.Unmarshal(bytes, &array)
	if err != nil {
		return nil, err
	}

	for _, user := range array {
		user.Inbox = make([]Message, 0)
		user.Login = make(chan LoginRequest)
		user.Send = make(chan SendRequest)
		user.Recv = make(chan RecvRequest)
		user.UserInfo = make(chan UserInfoRequest)
	}

	users := &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[int64]*User),
		filename: filename,
	}
	for _, user := range array {
		_, found := users.name_map[user.Name]
		if found {
			return nil, fmt.Errorf("duplicate user name %s", user.Name)
		}
		_, found = users.id_map[user.Id]
		if found {
			return nil, fmt.Errorf("duplicate user token %d", user.Token)
		}
		users.name_map[user.Name] = user
		users.id_map[user.Id] = user
	}
	return users, nil
}

func (users *Users) store() error {
	jsonData, err := func() ([]byte, error) {
		users_vec := make([]*User, 0, len(users.name_map))
		for _, user := range users.name_map {
			users_vec = append(users_vec, user)
		}
		sort.Slice(users_vec, func(i, j int) bool {
			return users_vec[i].Name < users_vec[j].Name
		})

		return json.MarshalIndent(users_vec, "", "\t")
	}()
	if err != nil {
		return err
	}

	jsonData = append(jsonData, '\n')
	err = os.WriteFile(users.filename, jsonData, 0644)
	if err != nil {
		return err
	}

	return nil
}

func __gen_unique_id(users *Users) (int64, error) {
	var id int64
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

func (users *Users) add(name string, passwd []byte) (*User, error) {
	_, found := users.name_map[name]
	if found {
		return nil, fmt.Errorf("user name %s already exists", name)
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
		Name:   name,
		Salt:   salt,
		Passwd: hashed_passwd,
		Id:     id,
	}
	users.name_map[name] = user
	users.id_map[id] = user

	err = users.store()
	if err != nil {
		delete(users.name_map, name)
		delete(users.id_map, id)
		return nil, err
	}

	return user, nil
}

func (user *User) check_passwd(passwd []byte) bool {
	hashed_passwd := __hash_passwd(passwd, user.Salt)
	return bytes.Equal(hashed_passwd, user.Passwd)
}

func user_handler(user *User) {
	for {
		select {
		case login := <-user.Login:
			login.Response <- login_handler(user, login)
		case send := <-user.Send:
			send.Response <- send_handler(user, send)
		case recv := <-user.Recv:
			recv.Response <- recv_handler(user, recv)
		case userInfo := <-user.UserInfo:
			userInfo.Response <- userInfo_handler(user)
		}
	}
}

var usersList atomic.Pointer[Users]

func main() {
	users, err := load_users(configPath("users.json"))
	if err != nil {
		fmt.Println("Error loading users:", err)
		return
	}
	//users.add("admin", hash("admin"))
	//users.add("jiri", hash("pwd123"))
	//users.add("zuzka", hash("pwd456"))
	//err = users.store()
	//if err != nil {
	//	fmt.Println("Error storing users:", err)
	//	return
	//}
	//return
	go persistence(users.clone())
	usersList.Store(users)
	for _, user := range users.id_map {
		go user_handler(user)
	}

	// TODO - start user handlers

	http.HandleFunc("/api/login", login_http_handler)
	http.HandleFunc("/api/send", send_http_handler)
	http.HandleFunc("/api/recv", recv_http_handler)
	http.HandleFunc("/api/userInfo", userInfo_http_handler)

	fmt.Println("Starting server at http://localhost:9443")

	if err := http.ListenAndServeTLS(":9443", "secrets/server.pem", "secrets/server.key", nil); err != nil {
		fmt.Println("Error starting server:", err)
	}
}
