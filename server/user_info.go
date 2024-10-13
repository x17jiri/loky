package main

import (
	"encoding/json"
	"net/http"
)

type UserInfoRequest struct {
	Response chan<- UserInfoResponse
}

type UserInfoResponse struct {
	Id uint64
}

func userInfo_handler(user *User) UserInfoResponse {
	return UserInfoResponse{
		Id: user.Id,
	}
}

type UserInfoHTTPInput struct {
	Name string `json:"name"`
}

type UserInfoHTTPOutput struct {
	Id uint64 `json:"id"`
}

func userInfo_http_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 1024)

	var input UserInfoHTTPInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	user := usersList.Load().userByName(input.Name)
	if user == nil {
		http.Error(w, "User not found", http.StatusNotFound)
		return
	}

	respChan := make(chan UserInfoResponse)
	defer close(respChan)

	req := UserInfoRequest{
		Response: respChan,
	}
	user.UserInfo <- req
	resp := <-respChan

	err = json.NewEncoder(w).Encode(UserInfoHTTPOutput{
		Id: resp.Id,
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
