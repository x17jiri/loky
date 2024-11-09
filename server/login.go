package main

import (
	"encoding/json"
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

	Err *RestAPIError `json:"-"`
}

func login_http_handler(w http.ResponseWriter, r *http.Request) {
	Log.i("=================================================================================")
	Log.i("login_handler")
	r.Body = http.MaxBytesReader(w, r.Body, 2048)

	var req LoginRequest
	err := json.NewDecoder(r.Body).Decode(&req)
	if err != nil {
		msg := "login: error decoding input: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusBadRequest))
		return
	}

	user := usersList.Load().userByName(req.Username)
	if user == nil || !user.check_passwd(req.Passwd) {
		msg := "login: invalid user/password"
		restAPIerror(w, NewError(msg, http.StatusUnauthorized))
		return
	}

	respChan := make(chan LoginResponse)
	defer close(respChan)

	req.Response = respChan
	user.Login <- req
	resp := <-respChan

	if resp.Err != nil {
		msg := "login: error: " + resp.Err.Error()
		restAPIerror(w, NewError(msg, http.StatusInternalServerError))
		return
	}

	err = json.NewEncoder(w).Encode(resp)
	if err != nil {
		msg := "login: error encoding response: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusInternalServerError))
	}
}

func login_synchronized_handler(user *User, req LoginRequest) LoginResponse {
	var err error
	user.Bearer, err = makeBearer(user.Id)
	if err != nil {
		return LoginResponse{
			Err: NewError("login: creating bearer", http.StatusInternalServerError),
		}
	}

	if req.Key != user.Key {
		// user changed their signing key
		user.Key = req.Key
		// all prekeys are signed with the old key, so they are invalid
		user.Prekeys = user.Prekeys[:0]
		// TODO - should we clear the inbox?
	}

	// persist the new credentials
	user.save_user()

	return LoginResponse{
		Bearer:      user.Bearer,
		NeedPrekeys: user.needPrekeys(),
	}
}
