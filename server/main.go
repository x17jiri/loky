package main

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"golang.org/x/crypto/pbkdf2"
)

type Payload struct {
	Timestamp time.Time
	Data      []byte
}

const QUEUE_SIZE int = 512

type Queue struct {
	data [QUEUE_SIZE]Payload
	head int
}

func (queue *Queue) add(payload Payload) {
	queue.data[queue.head] = payload
	queue.head = (queue.head + 1) % QUEUE_SIZE
}

type Connection struct {
	from uint64
	to   uint64
}

type Connections struct {
	mutex sync.RWMutex
	data  map[Connection]*Queue
}

type User struct {
	Name   string `json:"name"`
	Salt   []byte `json:"salt"`   // salt for password hashing
	Passwd []byte `json:"passwd"` // hashed password

	Id    uint64 `json:"id"`    // used instead of name in all operations except login
	Token []byte `json:"token"` // used instead of password in all operations except login

	Key []byte `json:"key"` // public key for encryption
}

type Users struct {
	mutex    sync.RWMutex
	name_map map[string]*User
	id_map   map[uint64]*User
	filename string // path to users.json
}

func read_file(filename string) ([]byte, error) {
	file, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	return io.ReadAll(file)
}

func load_users(filename string) (*Users, error) {
	// check if file exists
	if _, err := os.Stat(filename); os.IsNotExist(err) {
		return &Users{
			name_map: make(map[string]*User),
			id_map:   make(map[uint64]*User),
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

	users := &Users{
		name_map: make(map[string]*User),
		id_map:   make(map[uint64]*User),
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
	if users.filename == "" {
		return nil
	}
	return users.store_users(users.filename)
}

func (users *Users) store_users(filename string) error {
	jsonData, err := func() ([]byte, error) {
		users.mutex.RLock()
		defer users.mutex.RUnlock()

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
	err = os.WriteFile(filename, jsonData, 0644)
	if err != nil {
		return err
	}

	return nil
}

func __gen_salt() ([]byte, error) {
	salt := make([]byte, 16)
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

func __gen_unique_id(users *Users) (uint64, error) {
	users.mutex.RLock()
	defer users.mutex.RUnlock()

	var id uint64
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

func __hash_passwd(password []byte, salt []byte) []byte {
	return pbkdf2.Key(password, salt, 16, 32, sha256.New)
}

func (users *Users) add(name string, passwd string) (*User, error) {
	_, found := users.name_map[name]
	if found {
		return nil, fmt.Errorf("user name %s already exists", name)
	}

	salt, err := __gen_salt()
	if err != nil {
		return nil, err
	}

	hashed_passwd := __hash_passwd([]byte(passwd), salt)

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
	func() {
		users.mutex.Lock()
		defer users.mutex.Unlock()

		users.name_map[name] = user
		users.id_map[id] = user
	}()

	err = users.store()
	if err != nil {
		// rollback
		func() {
			users.mutex.Lock()
			defer users.mutex.Unlock()

			delete(users.name_map, name)
			delete(users.id_map, id)
		}()
		return nil, err
	}

	return user, nil
}

func (users *Users) check_passwd(name string, passwd []byte) *User {
	users.mutex.RLock()
	defer users.mutex.RUnlock()

	user, found := users.name_map[name]
	if !found {
		return nil
	}

	hashed_passwd := __hash_passwd(passwd, user.Salt)
	if !bytes.Equal(hashed_passwd, user.Passwd) {
		return nil
	}
	return user
}

type LoginInput struct {
	Name   string `json:"name"`
	Passwd []byte `json:"passwd"`
	Key    []byte `json:"key"`
}

type LoginOutput struct {
	Id    uint64 `json:"id"`
	Token []byte `json:"token"`
}

var users *Users

func login_handler(w http.ResponseWriter, r *http.Request) {
	fmt.Println("login_handler")
	r.Body = http.MaxBytesReader(w, r.Body, 16384)

	//	bodyBytes, err := io.ReadAll(r.Body)
	//	fmt.Println("login_handler: body = ", string(bodyBytes))

	var input LoginInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		fmt.Println("login_handler: error decoding input:", err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	// TODO - passwd is byte array now
	user := users.check_passwd(input.Name, input.Passwd)
	if user == nil {
		fmt.Println("login_handler: invalid password")
		http.Error(w, "Invalid password", http.StatusUnauthorized)
		return
	}

	var token []byte = make([]byte, 16)
	_, err = rand.Read(token)
	if err != nil {
		fmt.Println("login_handler: error generating token:", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	var id uint64
	func() {
		users.mutex.Lock()
		defer users.mutex.Unlock()

		id = user.Id
		user.Token = token
		if !bytes.Equal(input.Key, user.Key) {
			user.Key = input.Key
		}
	}()

	users.store()

	output := LoginOutput{
		Id:    id,
		Token: token,
	}
	json.NewEncoder(w).Encode(output)
}

type GetKeysInput []uint64

type GetKeysOutput []struct {
	Id  uint64 `json:"id"`
	Key []byte `json:"key"`
}

func getKeys_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 4096)

	var input GetKeysInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	users.mutex.RLock()
	defer users.mutex.RUnlock()

	output := make(GetKeysOutput, 0, len(input))
	for _, id := range input {
		user, found := users.id_map[id]
		if !found {
			continue
		}
		output = append(output, struct {
			Id  uint64 `json:"id"`
			Key []byte `json:"key"`
		}{
			Id:  user.Id,
			Key: user.Key,
		})
	}
	json.NewEncoder(w).Encode(output)
}

var connections Connections

type WriteInputItem struct {
	ReceiverId uint64 `json:"receiverId"`
	Payload    []byte `json:"payload"`
}

type WriteInput struct {
	Id      uint64           `json:"id"`
	Token   []byte           `json:"token"`
	Payload []WriteInputItem `json:"payload"`
}

func write_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 16384)

	var input WriteInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	now := time.Now()

	var user *User
	func() {
		users.mutex.RLock()
		defer users.mutex.RUnlock()

		var found bool
		user, found = users.id_map[input.Id]
		if !found || !bytes.Equal(input.Token, user.Token) {
			user = nil
		}
	}()
	if user == nil {
		http.Error(w, "Invalid id/token", http.StatusUnauthorized)
		return
	}

	connections.mutex.Lock()
	defer connections.mutex.Unlock()

	for _, item := range input.Payload {
		connection := Connection{
			from: input.Id,
			to:   item.ReceiverId,
		}
		queue, found := connections.data[connection]
		if !found {
			queue = &Queue{}
			connections.data[connection] = queue
		}

		queue.add(Payload{
			Timestamp: now,
			Data:      item.Payload,
		})
	}
}

type ReadInput struct {
	Id        uint64   `json:"id"`
	Token     []byte   `json:"token"`
	Requested []uint64 `json:"requested"`
}

type ReadOutput struct {
	Items []ReadOutputItem `json:"items"`
}

type ReadOutputItem struct {
	Id      uint64                  `json:"id"`
	Payload []ReadOutputPayloadItem `json:"payload"`
}

type ReadOutputPayloadItem struct {
	Age  uint64 `json:"age"`
	Data []byte `json:"data"`
}

func read_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 4096)

	var input ReadInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	now := time.Now()

	var user *User
	func() {
		users.mutex.RLock()
		defer users.mutex.RUnlock()

		var found bool
		user, found = users.id_map[input.Id]
		if !found || !bytes.Equal(input.Token, user.Token) {
			user = nil
		}
	}()
	if user == nil {
		http.Error(w, "Invalid id/token", http.StatusUnauthorized)
		return
	}

	connections.mutex.RLock()
	defer connections.mutex.RUnlock()

	output := ReadOutput{
		Items: make([]ReadOutputItem, 0, len(input.Requested)),
	}
	for _, requestedId := range input.Requested {
		connection := Connection{
			from: requestedId,
			to:   input.Id,
		}
		queue, found := connections.data[connection]
		if !found {
			continue
		}

		items := make([]ReadOutputPayloadItem, 0, QUEUE_SIZE)
		for j := 0; j < QUEUE_SIZE; j++ {
			i := (queue.head - 1 - j + QUEUE_SIZE) % QUEUE_SIZE
			item := queue.data[i]
			age := uint64(now.Sub(item.Timestamp).Seconds())
			if age > 2*3600 {
				break
			}
			items = append(items, ReadOutputPayloadItem{
				Age:  age,
				Data: item.Data,
			})
		}

		output.Items = append(output.Items, ReadOutputItem{
			Id:      requestedId,
			Payload: items,
		})
	}
}

func configPath(filename string) string {
	return filepath.Join(".", filename)
	/*
	   // Get the absolute path of the executable
	   execPath, err := os.Executable()

	   	if err != nil {
	   		log.Fatal(err)
	   	}

	   // Get the directory where the executable is located
	   execDir := filepath.Dir(execPath)

	   // Construct the full path to the "users.json" file
	   return filepath.Join(execDir, filename)
	*/
}

func main() {
	var err error
	users, err = load_users(configPath("users.json"))
	if err != nil {
		fmt.Println("Error loading users:", err)
		return
	}
	//users.add("admin", "admin")
	//users.add("x17jiri", "my stupefyingly insecure password")
	//users.add("zuzka", "my stupefyingly secure password")
	//err = users.store_users("users.json")
	//if err != nil {
	//	fmt.Println("Error storing users:", err)
	//	return
	//}
	//return

	http.HandleFunc("/api/login", login_handler)
	http.HandleFunc("/api/getKeys", getKeys_handler)
	http.HandleFunc("/api/write", write_handler)
	http.HandleFunc("/api/read", read_handler)

	fmt.Println("Starting server at http://localhost:9443")

	if err := http.ListenAndServeTLS(":9443", "secrets/server.pem", "secrets/server.key", nil); err != nil {
		fmt.Println("Error starting server:", err)
	}
}
