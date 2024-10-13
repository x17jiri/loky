package main

import (
	"bytes"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"net/http"
)

type LoginRequest struct {
	Name    string `json:"name"`
	Passwd  []byte `json:"passwd"`
	Key     []byte `json:"key"`
	KeyHash []byte `json:"keyHash"`

	Response chan<- LoginResponse `json:"-"`
}

type LoginResponse struct {
	Id    uint64 `json:"id"`
	Token []byte `json:"token"`

	Err error `json:"-"`
}

func login_handler(user *User, req LoginRequest) LoginResponse {
	var token []byte = make([]byte, 16)
	_, err := rand.Read(token)
	if err != nil {
		return LoginResponse{
			Err: err,
		}
	}

	user.Token = token
	if !bytes.Equal(req.Key, user.Key) {
		user.Key = req.Key
		user.KeyHash = req.KeyHash
		user.Inbox = user.Inbox[:0]
	}

	UpdateCred <- UpdateCredRequest{
		Id:      user.Id,
		Token:   user.Token,
		Key:     user.Key,
		KeyHash: user.KeyHash,
	}

	return LoginResponse{
		Id:    user.Id,
		Token: token,
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

	user := usersList.Load().userByName(req.Name)
	if user == nil || !user.check_passwd(req.Passwd) {
		http.Error(w, "Invalid user/password", http.StatusUnauthorized)
		return
	}

	respChan := make(chan LoginResponse)
	defer close(respChan)

	fmt.Println("login_handler: sending request to login_handler")
	req.Response = respChan
	fmt.Println("login_handler: req = ", req)
	user.Login <- req
	fmt.Println("login_handler: waiting for response")
	resp := <-respChan
	fmt.Println("login_handler: response = ", resp)

	if resp.Err != nil {
		http.Error(w, resp.Err.Error(), http.StatusInternalServerError)
		return
	}

	err = json.NewEncoder(w).Encode(resp)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
