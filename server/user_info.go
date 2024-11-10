package main

import (
	"net/http"
)

func userInfo_http_handler(w http.ResponseWriter, r *http.Request) {
	restAPI_handler(w, r, "userInfo", 1024, userInfo_restAPI_handler)
}

type UserInfoRequest struct {
	Username string `json:"username"`

	Response chan<- UserInfoResponse `json:"-"`
}

type UserInfoResponse struct {
	Id         string `json:"id"`
	SigningKey string `json:"sign_key"`   // public key for signing
	MasterKey  string `json:"master_key"` // master public key for diffie-hellman key exchange

	Err *RestAPIError `json:"-"`
}

func userInfo_restAPI_handler(_ *User, req UserInfoRequest) (UserInfoResponse, *RestAPIError) {
	// The `user` we've got as a param is the user asking the info,
	// not the user we're asking about.
	// So get the right user from the list.
	user := usersList.Load().userByName(req.Username)
	if user == nil {
		return UserInfoResponse{}, NewError("User not found", http.StatusNotFound)
	}

	respChan := make(chan UserInfoResponse)
	defer close(respChan)

	req.Response = respChan

	// Writing to this channel will result in a call to the synchronized handler
	user.UserInfo <- req
	resp := <-respChan

	return resp, resp.Err
}

// The synchronized handler is called from `user_handler()` and is synchronized
// so that only one thread at a time can access the user's data.
func userInfo_synchronized_handler(user *User) UserInfoResponse {
	return UserInfoResponse{
		Id:         user.EncryptedID.toString(),
		SigningKey: user.SigningKey,
		MasterKey:  user.MasterKey,
	}
}
