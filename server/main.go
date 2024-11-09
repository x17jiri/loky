package main

import (
	"net/http"
)

func main() {
	if true {
		list := []struct {
			username string
			passwd   string
		}{
			{"jiri", "M5'*~:CS(-cDo>/~[DiY"},
			{"zuzka", "$},5R#]@,2$R'f?gLh/5"},
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
