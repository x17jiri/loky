package main

import (
	"net/http"
	"time"
)

// 2000-01-01 00:00:00 UTC
var referenceTime = time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC)

const SWITCH_INBOX_SEC int64 = 30 * 60
const MSG_EXPIRE_SEC int64 = 2 * 60 * 60

const PREKEY_COUNT = 100
const PREKEY_MAX_COUNT = 2 * PREKEY_COUNT

func appPath() string {
	return "."
}

func main() {
	if false {
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
				Log.e("Error adding user: %s", err.Error())
				return
			}
		}
		Log.i("Users created")
		return
	}

	users, err := load_users()
	if err != nil || users == nil {
		Log.e("Error loading users: %s", err.Error())
		return
	}
	if len(users.id_map) == 0 {
		Log.w("Warning: no users loaded")
	}
	usersList.Store(users)
	for _, user := range users.id_map {
		go user_handler(user)
	}

	http.HandleFunc("/api/login", login_http_handler)
	http.HandleFunc("/api/send", send_http_handler)
	http.HandleFunc("/api/recv", recv_http_handler)
	http.HandleFunc("/api/userInfo", userInfo_http_handler)
	http.HandleFunc("/api/fetchPrekeys", fetchPrekeys_http_handler)
	http.HandleFunc("/api/addPrekeys", addPrekeys_http_handler)

	Log.i("Starting server at http://localhost:11443")

	err = http.ListenAndServeTLS(":11443", "secrets/server.pem", "secrets/server.key", nil)
	if err != nil {
		Log.e("Error starting server: %s", err.Error())
	}
}
