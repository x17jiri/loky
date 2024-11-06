package main

import (
	"net/http"
)

type RecvRequest struct {
	Response chan<- RecvResponse `json:"-"`
}

type RecvResponse struct {
	Items []Message `json:"items"`

	Err *RestAPIError `json:"-"`
}

func recv_http_handler(w http.ResponseWriter, r *http.Request) {
	restAPI_handler(w, r, "recv", 4096, recv_restAPI_handler)
}

func recv_restAPI_handler(user *User, req RecvRequest) (RecvResponse, *RestAPIError) {
	respChan := make(chan RecvResponse)
	defer close(respChan)

	req.Response = respChan

	user.Recv <- req
	resp := <-respChan

	return resp, resp.Err
}

func recv_synchronized_handler(user *User) RecvResponse {
	msgs, err := user.Inbox.getMessages(monotonicSeconds())
	return RecvResponse{
		Items: msgs,
		Err:   err,
	}
}
