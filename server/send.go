package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"time"
)

type SendRequest struct {
	Message Message
	KeyHash []byte

	Response chan<- *SendResponse
}

type SendResponse struct {
	NewKey     []byte
	NewKeyHash []byte
}

func send_handler(user *User, req SendRequest) *SendResponse {
	if !bytes.Equal(req.KeyHash, user.KeyHash) {
		// The sender has an outdated key
		return &SendResponse{
			NewKey:     user.Key,
			NewKeyHash: user.KeyHash,
		}
	}

	user.Inbox = append(user.Inbox, req.Message)

	return nil
}

type SendHTTPInputItem struct {
	To      uint64 `json:"to"`
	Data    string `json:"data"`
	KeyHash []byte `json:"keyHash"` // This key was used to encrypt the message
}

type SendHTTPInput struct {
	Id    uint64              `json:"id"`
	Token []byte              `json:"token"`
	Items []SendHTTPInputItem `json:"items"`
}

type SendHTTPOutputItem struct {
	To      uint64 `json:"to"`
	Key     []byte `json:"key"`
	KeyHash []byte `json:"keyHash"`
}

type SendHTTPOutput []SendHTTPOutputItem

func send_http_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 16384)

	var input SendHTTPInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	users := usersList.Load()
	user := users.userById(input.Id)
	if user == nil || !bytes.Equal(input.Token, user.Token) {
		http.Error(w, "Invalid id/token", http.StatusUnauthorized)
		return
	}

	respChan := make(chan *SendResponse)
	defer close(respChan)

	now := time.Now()

	output := make([]SendHTTPOutputItem, 0)

	for _, item := range input.Items {
		toUser := users.userById(item.To)
		if toUser == nil {
			continue
		}

		sendReq := SendRequest{
			Message: Message{
				From:      user.Id,
				Timestamp: now,
				Data:      item.Data,
			},
			KeyHash:  item.KeyHash,
			Response: respChan,
		}
		toUser.Send <- sendReq
		resp := <-respChan
		if resp != nil {
			output = append(output, SendHTTPOutputItem{
				To:      item.To,
				Key:     resp.NewKey,
				KeyHash: resp.NewKeyHash,
			})
		}
	}

	err = json.NewEncoder(w).Encode(output)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
