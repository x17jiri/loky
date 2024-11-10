package main

import (
	"net/http"
)

func send_http_handler(w http.ResponseWriter, r *http.Request) {
	restAPI_handler(w, r, "send", 16384, send_restAPI_handler)
}

type SendItem struct {
	To   EncryptedID `json:"to"`
	Type string      `json:"type"`
	Msg  string      `json:"msg"`
}

type SendRequest struct {
	Items []SendItem `json:"items"`
}

type SendResponse struct {
	NeedPrekeys bool `json:"needPrekeys"`
}

func send_restAPI_handler(user *User, req SendRequest) (SendResponse, *RestAPIError) {
	respChan := make(chan PutResponse)
	defer close(respChan)

	var err *RestAPIError = nil

	Log.d("msg count = %d", len(req.Items))
	now := monotonicSeconds()
	from := user.EncryptedID.toString()
	users := usersList.Load()
	for _, item := range req.Items {
		toUser := users.userById(item.To.decrypt())
		if toUser == nil {
			//err = NewError("User not found", http.StatusNotFound)
			continue
		}

		toUser.Put <- PutRequest{
			Message: Message{
				Time: now,
				From: from,
				Type: item.Type,
				Msg:  item.Msg,
			},
			Response: respChan,
		}
		resp := <-respChan

		if resp.Err != nil {
			err = resp.Err
		}
	}

	return SendResponse{
		NeedPrekeys: user.needPrekeys(),
	}, err
}

type PutRequest struct {
	Message Message

	Response chan<- PutResponse
}

type PutResponse struct {
	Err *RestAPIError
}

func put_synchronized_handler(user *User, req PutRequest) PutResponse {
	return PutResponse{
		Err: user.Inbox.addMessage(&req.Message),
	}
}
