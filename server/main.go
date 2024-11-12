package main

import (
	"flag"
	"net/http"
)

func main() {
	create_cfg := flag.Bool("create-config", false, "create default config.json")
	new_user := flag.String("new-user", "", "create new user with given username")
	add_inv := flag.Int("add-inv", 0, "add n invitations to config")
	flag.Parse()

	config_json := "config.json"
	if *create_cfg {
		cfg, err := newConfig(config_json)
		if err != nil {
			Log.e("Error creating config: %s", err.Error())
			return
		}
		err = cfg.save()
		if err != nil {
			Log.e("Error saving config: %s", err.Error())
			return
		}
		Log.i("New config saved in %s", cfg.Filename)
		return
	}
	cfg, err := loadConfig(config_json)
	if err != nil {
		Log.e("Error loading config: %s", err.Error())
		return
	}
	config = cfg

	if *add_inv > 0 {
		for i := 0; i < *add_inv; i++ {
			code, err := randBytes(16)
			if err != nil {
				Log.e("Error generating invitation code: %s", err.Error())
				return
			}
			config.Invitations = append(config.Invitations, base64Encode(code))
		}
		err = config.save()
		if err != nil {
			Log.e("Error saving config: %s", err.Error())
			return
		}
		Log.i("Added %d invitations to config", *add_inv)
		return
	}

	users, err := load_users()
	if err != nil || users == nil {
		Log.e("Error loading users: %s", err.Error())
		return
	}

	if *new_user != "" {
		username := *new_user
		passwdBytes, err := randBytes(16)
		if err != nil {
			Log.e("Error generating password: %s", err.Error())
			return
		}
		passwd := base64Encode(passwdBytes)
		_, err = addUser(users, username, hash(passwd))
		if err != nil {
			Log.e("Error adding user: %s", err.Error())
			return
		}
		Log.i("Created user %s with password %s", username, passwd)
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
	http.HandleFunc("/api/reg", reg_http_handler)
	http.HandleFunc("/api/send", send_http_handler)
	http.HandleFunc("/api/recv", recv_http_handler)
	http.HandleFunc("/api/userInfo", userInfo_http_handler)
	http.HandleFunc("/api/fetchPrekeys", fetchPrekeys_http_handler)
	http.HandleFunc("/api/addPrekeys", addPrekeys_http_handler)

	Log.i("Starting server at http://localhost:9443")

	err = http.ListenAndServeTLS(":9443", "secrets/server.pem", "secrets/server.key", nil)
	if err != nil {
		Log.e("Error starting server: %s", err.Error())
	}
}
