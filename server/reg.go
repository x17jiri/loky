package main

import (
	"encoding/json"
	"net/http"
)

type RegRequest struct {
	Invitation string      `json:"invitation"`
	Username   string      `json:"username"`
	Passwd     Base64Bytes `json:"passwd"`
}

type RegResponse struct {
}

func reg_http_handler(w http.ResponseWriter, r *http.Request) {
	Log.i("=================================================================================")
	Log.i("reg_handler")
	r.Body = http.MaxBytesReader(w, r.Body, 1024)

	var req RegRequest
	err := json.NewDecoder(r.Body).Decode(&req)
	if err != nil {
		msg := "reg: error decoding input: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusBadRequest))
		return
	}

	GlobalLock.Lock()
	defer GlobalLock.Unlock()

	oldUsers := usersList.Load()
	user := oldUsers.userByName(req.Username)
	if user != nil {
		msg := "reg: user already exists"
		restAPIerror(w, NewError(msg, http.StatusConflict))
		return
	}

	// check if invitation is in config.Invitations
	index := -1
	for i, inv := range config.Invitations {
		if inv == req.Invitation {
			index = i
			break
		}
	}

	// if invitation is not found, return error
	if index < 0 {
		msg := "reg: invalid invitation"
		restAPIerror(w, NewError(msg, http.StatusForbidden))
		return
	}

	// remove invitation from config and log invitation usage
	config.Invitations = append(config.Invitations[:index], config.Invitations[index+1:]...)
	config.UsedInvitations = append(config.UsedInvitations, UsedInvitation{
		Code: req.Invitation,
		By:   req.Username,
	})
	config.save()

	// create new user
	newUsers, err := addUser(oldUsers, req.Username, req.Passwd)
	if err != nil {
		msg := "reg: error adding user: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusInternalServerError))
		return
	}
	usersList.Store(newUsers)

	err = json.NewEncoder(w).Encode(RegResponse{})
	if err != nil {
		msg := "reg: error encoding response: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusInternalServerError))
	}
}
