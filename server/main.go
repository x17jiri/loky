package main

import (
	"fmt"
	"net/http"
)

const PREKEY_COUNT = 100

func appPath() string {
	return "."
}

func main() {
	if true {
		list := []struct {
			username string
			passwd   string
		}{
			{"jiri", "mboPsxsthqm3Q0oVO2mv"},
			{"zuzka", "47OWzjXthHHKOOGjHbdB"},
		}
		users := newUsers()
		for _, u := range list {
			var err error
			users, err = addUser(users, u.username, hash(u.passwd))
			if err != nil {
				fmt.Println("Error adding user:", err)
				return
			}
		}
		fmt.Println("Users created")
		return
	}

	users, err := load_users()
	if err != nil {
		fmt.Println("Error loading users:", err)
		return
	}
	usersList.Store(users)
	for _, user := range users.id_map {
		go user_handler(user)
	}

	http.HandleFunc("/api/login", login_http_handler)
	http.HandleFunc("/api/send", send_http_handler)
	http.HandleFunc("/api/recv", recv_http_handler)
	http.HandleFunc("/api/userInfo", userInfo_http_handler)

	fmt.Println("Starting server at http://localhost:11443")

	err = http.ListenAndServeTLS(":11443", "secrets/server.pem", "secrets/server.key", nil)
	if err != nil {
		fmt.Println("Error starting server:", err)
	}
}
