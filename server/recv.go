package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"time"
)

type RecvRequest struct {
	Response chan<- RecvResponse
}

type RecvResponse []Message

func recv_handler(user *User, req RecvRequest) RecvResponse {
	var inbox = user.Inbox
	user.Inbox = make([]Message, 0)
	return inbox
}

type RecvHTTPInput struct {
	Id    uint64 `json:"id"`
	Token []byte `json:"token"`
}

type RecvOutput []RecvOutputItem

type RecvOutputItem struct {
	From uint64 `json:"from"`
	Age  uint64 `json:"age"`
	Data string `json:"data"`
}

func recv_http_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 4096)

	var input RecvHTTPInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	user := usersList.Load().userById(input.Id)
	if user == nil || !bytes.Equal(input.Token, user.Token) {
		http.Error(w, "Invalid id/token", http.StatusUnauthorized)
		return
	}

	respChan := make(chan RecvResponse)
	defer close(respChan)

	req := RecvRequest{
		Response: respChan,
	}
	user.Recv <- req
	resp := <-respChan

	now := time.Now()

	output := make([]RecvOutputItem, 0, len(resp))
	for _, msg := range resp {
		output = append(output, RecvOutputItem{
			From: msg.From,
			Age:  uint64(now.Sub(msg.Timestamp).Seconds()),
			Data: msg.Data,
		})
	}

	err = json.NewEncoder(w).Encode(output)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
