package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

type LoginRequest struct {
	Username string      `json:"username"`
	Passwd   Base64Bytes `json:"passwd"`
	Key      string      `json:"key"`

	Response chan<- LoginResponse `json:"-"`
}

type LoginResponse struct {
	Bearer      Bearer `json:"bearer"`
	NeedPrekeys bool   `json:"needPrekeys"`

	Err error `json:"-"`
}

func login_handler(user *User, req LoginRequest) LoginResponse {
	var err error
	user.Bearer, err = makeBearer(user.Id)
	if err != nil {
		return LoginResponse{Err: err}
	}

	if req.Key != user.Key {
		// user changed their signing key
		user.Key = req.Key
		// all prekeys are signed with the old key, so they are invalid
		user.Prekeys = user.Prekeys[:0]
		// TODO - should we clear the inbox?
		user.Inbox = user.Inbox[:0]
	}

	// persist the new credentials
	UpdateCred <- UpdateCredRequest{
		Id:     user.Id,
		Bearer: user.Bearer,
		Key:    user.Key,
	}

	return LoginResponse{
		Bearer:      user.Bearer,
		NeedPrekeys: user.needPrekeys(),
	}
}

func login_http_handler(w http.ResponseWriter, r *http.Request) {
	fmt.Println("login_handler")
	r.Body = http.MaxBytesReader(w, r.Body, 16384)

	//	bodyBytes, _ := io.ReadAll(r.Body)
	//	fmt.Println("login_handler: body = ", string(bodyBytes))

	var req LoginRequest
	err := json.NewDecoder(r.Body).Decode(&req)
	if err != nil {
		fmt.Println("login_handler: error decoding input:", err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	fmt.Println("login_handler: req = ", req)

	user := usersList.Load().userByName(req.Username)
	if user == nil || !user.check_passwd(req.Passwd) {
		fmt.Println("login_handler: invalid user/password")
		http.Error(w, "Invalid user/password", http.StatusUnauthorized)
		return
	}

	respChan := make(chan LoginResponse)
	defer close(respChan)

	req.Response = respChan
	user.Login <- req
	resp := <-respChan

	if resp.Err != nil {
		fmt.Println("login_handler: error:", resp.Err)
		http.Error(w, resp.Err.Error(), http.StatusInternalServerError)
		return
	}

	err = json.NewEncoder(w).Encode(resp)
	if err != nil {
		fmt.Println("login_handler: error encoding output:", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
