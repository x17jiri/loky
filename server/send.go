package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

// 2000-01-01 00:00:00 UTC
var referenceTime = time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC)

func monotonicSeconds() int64 {
	return int64(time.Since(referenceTime).Seconds())
}

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

	// Remove from the inbox any messages older than 2 hours
	now := req.Message.Timestamp
	cutoff := now - 7200

	// The messages in the inbox are sorted by timestamp,
	// so we can find the first message that is not expired
	// and all following messages are not expired as well.
	expired := 0
	for expired = 0; expired < len(user.Inbox); expired++ {
		if user.Inbox[expired].Timestamp > cutoff {
			break
		}
	}
	if expired > 0 {
		// Remove the expired messages
		user.Inbox = user.Inbox[expired:]
	}

	// Add the new message to the inbox
	user.Inbox = append(user.Inbox, req.Message)

	return nil
}

type SendHTTPInputItem struct {
	To      int64  `json:"to"`
	Data    string `json:"data"`
	KeyHash []byte `json:"keyHash"` // This key was used to encrypt the message
}

type SendHTTPInput struct {
	Id    int64               `json:"id"`
	Token []byte              `json:"token"`
	Items []SendHTTPInputItem `json:"items"`
}

type SendHTTPOutputItem struct {
	To      int64  `json:"to"`
	Key     []byte `json:"key"`
	KeyHash []byte `json:"keyHash"`
}

type SendHTTPOutput struct {
	UpdatedKeys []SendHTTPOutputItem `json:"updatedKeys"`
}

func send_http_handler(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 16384)

	var input SendHTTPInput
	err := json.NewDecoder(r.Body).Decode(&input)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	fmt.Println("send_http_handler: input = ", input)

	users := usersList.Load()
	user := users.userById(input.Id)
	if user == nil || !bytes.Equal(input.Token, user.Token) {
		http.Error(w, "Invalid id/token", http.StatusUnauthorized)
		return
	}

	respChan := make(chan *SendResponse)
	defer close(respChan)

	now := monotonicSeconds()

	output := SendHTTPOutput{
		UpdatedKeys: make([]SendHTTPOutputItem, 0),
	}

	for _, item := range input.Items {
		toUser := users.userById(item.To)
		if toUser == nil /*|| user.Id == toUser.Id*/ {
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
			output.UpdatedKeys = append(output.UpdatedKeys, SendHTTPOutputItem{
				To:      item.To,
				Key:     resp.NewKey,
				KeyHash: resp.NewKeyHash,
			})
		}
	}

	fmt.Println("send_http_handler: output = ", output)

	err = json.NewEncoder(w).Encode(output)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
