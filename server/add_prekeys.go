package main

import (
	"fmt"
	"net/http"
)

type AddPrekeysRequest struct {
	Prekeys []string `json:"prekeys"`

	Response chan<- AddPrekeysResponse `json:"-"`
}

type AddPrekeysResponse struct {
	LivePrekeys []string `json:"live_prekeys"`

	Err *RestAPIError `json:"-"`
}

func addPrekeys_http_handler(w http.ResponseWriter, r *http.Request) {
	restAPI_handler(w, r, "addPrekeys", 16384, addPrekeys_restAPI_handler)
}

func addPrekeys_restAPI_handler(user *User, req AddPrekeysRequest) (AddPrekeysResponse, *RestAPIError) {
	respChan := make(chan AddPrekeysResponse)
	defer close(respChan)

	req.Response = respChan

	user.AddPrekeys <- req
	resp := <-respChan

	return resp, resp.Err
}

func addPrekeys_synchronized_handler(user *User, req AddPrekeysRequest) AddPrekeysResponse {
	if len(user.Prekeys)+len(req.Prekeys) > PREKEY_MAX_COUNT {
		return AddPrekeysResponse{
			LivePrekeys: user.Prekeys,
			Err:         nil,
		}
	}

	fmt.Println("Current prekeys:", user.Prekeys)
	fmt.Println("Adding prekeys:", req.Prekeys)

	user.Prekeys = append(user.Prekeys, req.Prekeys...)

	fmt.Println("New prekeys:", user.Prekeys)

	_ = user.save_user()

	return AddPrekeysResponse{
		LivePrekeys: user.Prekeys,
		Err:         nil,
	}
}
