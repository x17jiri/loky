package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
)

type RestAPIError struct {
	Err  string
	Code int
}

func (e *RestAPIError) Error() string {
	return "RestAPIError: " + e.Err
}

func NewError(err string, code int) *RestAPIError {
	return &RestAPIError{Err: err, Code: code}
}

func restAPIerror(w http.ResponseWriter, err *RestAPIError) {
	msg := err.Error()
	code := err.Code
	fmt.Println(msg)
	http.Error(w, msg, code)
}

func restAPI_handler[
	Request any,
	Response any,
](
	w http.ResponseWriter,
	r *http.Request,

	handlerName string,
	maxRequestSize int64,
	handler func(*User, Request) (Response, *RestAPIError),
) {
	fmt.Println("=================================================================================")
	fmt.Println(monotonicSeconds(), handlerName)
	r.Body = http.MaxBytesReader(w, r.Body, maxRequestSize)

	authHeader := r.Header.Get("Authorization")
	token := strings.TrimPrefix(authHeader, "Bearer ")
	if authHeader == token {
		msg := handlerName + ": authorization: bearer not found"
		restAPIerror(w, NewError(msg, http.StatusUnauthorized))
		return
	}
	bearer := Bearer(token)

	userId, err := bearer.toUserID()
	if err != nil {
		msg := handlerName + ": authorization: cannot parse bearer: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusUnauthorized))
		return
	}

	user := usersList.Load().userById(userId)
	if user == nil || bearer == "" || user.Bearer != bearer {
		msg := handlerName + ": authorization: invalid bearer"
		restAPIerror(w, NewError(msg, http.StatusUnauthorized))
		return
	}

	var req Request
	err = json.NewDecoder(r.Body).Decode(&req)
	if err != nil {
		msg := handlerName + ": decoding request: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusBadRequest))
		return
	}

	resp, restAPIerr := handler(user, req)
	if restAPIerr != nil {
		restAPIerror(w, restAPIerr)
		return
	}

	err = json.NewEncoder(w).Encode(&resp)
	if err != nil {
		msg := handlerName + ": encoding response: " + err.Error()
		restAPIerror(w, NewError(msg, http.StatusInternalServerError))
		return
	}
}
