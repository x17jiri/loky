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

type EncryptedLoc struct {
	Encrypted [16]byte
}

type LocWithTime struct {
	Timestamp time.Time
	Location  EncryptedLoc
}

type LocBuffer struct {
	data [512]LocWithTime
	head int
}

const MAX_GROUPS = 16

type User struct {
	Name   string                 `json:"name"`
	Token  uint64                 `json:"token"`
	Salt   []byte                 `json:"salt"`
	Passwd []byte                 `json:"passwd"`
	Key    []byte                 `json:"-"`
	Groups [MAX_GROUPS]*LocBuffer `json:"-"`
}

type Users struct {
	mutex     sync.RWMutex
	name_map  map[string]*User
	token_map map[uint64]*User
	filename  string
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
			name_map:  make(map[string]*User),
			token_map: make(map[uint64]*User),
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
		name_map:  make(map[string]*User),
		token_map: make(map[uint64]*User),
		filename:  filename,
	}
	for _, user := range array {
		_, found := users.name_map[user.Name]
		if found {
			return nil, fmt.Errorf("duplicate user name %s", user.Name)
		}
		_, found = users.token_map[user.Token]
		if found {
			return nil, fmt.Errorf("duplicate user token %d", user.Token)
		}
		users.name_map[user.Name] = user
		users.token_map[user.Token] = user
		for i := range user.Groups {
			user.Groups[i] = &LocBuffer{}
		}
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

func __gen_token() (uint64, error) {
	token := make([]byte, 8)
	_, err := rand.Read(token)
	if err != nil {
		return 0, err
	}
	return binary.LittleEndian.Uint64(token), nil
}

func __gen_unique_token(users *Users) (uint64, error) {
	users.mutex.RLock()
	defer users.mutex.RUnlock()

	var token uint64
	var err error
	var i int
	for i = 0; i < 10; i++ {
		token, err = __gen_token()
		if err != nil {
			return 0, err
		}
		_, found := users.token_map[token]
		if !found {
			break
		}
	}
	if i == 10 {
		for {
			token++
			_, found := users.token_map[token]
			if !found {
				break
			}
		}
	}
	return token, nil
}

func __hash_passwd(password []byte, salt []byte) []byte {
	return pbkdf2.Key(password, salt, 4, 32, sha256.New)
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

	token, err := __gen_unique_token(users)
	if err != nil {
		return nil, err
	}

	user := &User{
		Name:   name,
		Token:  token,
		Salt:   salt,
		Passwd: hashed_passwd,
	}
	func() {
		users.mutex.Lock()
		defer users.mutex.Unlock()

		users.name_map[name] = user
		users.token_map[token] = user
	}()

	err = users.store()
	if err != nil {
		// rollback
		func() {
			users.mutex.Lock()
			defer users.mutex.Unlock()

			delete(users.name_map, name)
			delete(users.token_map, token)
		}()
		return nil, err
	}

	for i := range user.Groups {
		user.Groups[i] = &LocBuffer{}
	}

	return user, nil
}

func (users *Users) check_passwd(name string, passwd string) *User {
	users.mutex.RLock()
	defer users.mutex.RUnlock()

	user, found := users.name_map[name]
	if !found {
		return nil
	}

	hashed_passwd := __hash_passwd([]byte(passwd), user.Salt)
	if !bytes.Equal(hashed_passwd, user.Passwd) {
		return nil
	}
	return user
}

type LoginInput struct {
	Name   string `json:"name"`
	Passwd string `json:"passwd"`
}

type LoginOutput struct {
	Token uint64 `json:"token"`
	Key   []byte `json:"key"`
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

	user := users.check_passwd(input.Name, input.Passwd)
	if user == nil {
		fmt.Println("login_handler: invalid password")
		http.Error(w, "Invalid password", http.StatusUnauthorized)
		return
	}

	var key []byte = make([]byte, 16)
	_, err = rand.Read(key)
	if err != nil {
		fmt.Println("login_handler: error generating key:", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	var token uint64
	func() {
		users.mutex.Lock()
		defer users.mutex.Unlock()

		token = user.Token
		user.Key = key
	}()

	users.store()

	output := LoginOutput{
		Token: token,
		Key:   key,
	}
	json.NewEncoder(w).Encode(output)
}

type ReadInput struct {
	Token uint64 `json:"token"`
	Group uint8  `json:"group"`
}

type ReadOutputItem struct {
	Age uint64      `json:"age"`
	Loc LocWithTime `json:"loc"`
}

type ReadOutput struct {
	Locs []ReadOutputItem `json:"locs"`
}

func read_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 16384)

	var input ReadInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	now := time.Now()

	users.mutex.RLock()
	defer users.mutex.RUnlock()

	user, found := users.token_map[input.Token]
	if !found {
		http.Error(w, "Invalid token", http.StatusNotFound)
		return
	}

	if input.Group >= MAX_GROUPS {
		http.Error(w, "Invalid group", http.StatusBadRequest)
		return
	}
	group := user.Groups[input.Group]

	C := len(group.data)
	H := group.head
	result := make([]ReadOutputItem, 0, C)

	for j := 0; j < C; j++ {
		i := (H - j - 1 + C) % C
		age := uint64(now.Sub(group.data[i].Timestamp).Seconds())
		if age > 2*3600 {
			break
		}
		result = append(result, ReadOutputItem{
			Age: age,
			Loc: group.data[i],
		})
	}

	output := ReadOutput{
		Locs: result,
	}
	json.NewEncoder(w).Encode(output)
}

type WriteInputItem struct {
	Group uint8        `json:"group"`
	Loc   EncryptedLoc `json:"loc"`
}

type WriteInput struct {
	Token uint64           `json:"token"`
	Key   []byte           `json:"key"`
	Locs  []WriteInputItem `json:"locs"`
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

	users.mutex.RLock()
	defer users.mutex.RUnlock()

	user, found := users.token_map[input.Token]
	if !found {
		http.Error(w, "Invalid token", http.StatusNotFound)
		return
	}

	for _, item := range input.Locs {
		if item.Group >= MAX_GROUPS {
			http.Error(w, "Invalid group", http.StatusBadRequest)
			return
		}
		group := user.Groups[item.Group]

		H := group.head
		group.data[H] = LocWithTime{
			Timestamp: now,
			Location:  item.Loc,
		}
		group.head = (H + 1) % len(group.data)
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
	users.add("admin", "admin")
	//users.add("x17jiri", "my stupefyingly insecure password")
	//users.add("zuzka", "my stupefyingly secure password")
	//err = users.store_users("users.json")
	//if err != nil {
	//	fmt.Println("Error storing users:", err)
	//	return
	//}
	//return

	http.HandleFunc("/api/login", login_handler)
	http.HandleFunc("/api/read", read_handler)
	http.HandleFunc("/api/write", write_handler)

	fmt.Println("Starting server at http://localhost:9443")

	if err := http.ListenAndServeTLS(":9443", "secrets/server.pem", "secrets/server.key", nil); err != nil {
		fmt.Println("Error starting server:", err)
	}
}
