package main

import (
	"fmt"
	"net/http"
)

func fetchPrekeys_http_handler(w http.ResponseWriter, r *http.Request) {
	restAPI_handler(w, r, "fetchPrekeys", 512, fetchPrekeys_restAPI_handler)
}

type FetchPrekeysRequest struct {
	Ids []string `json:"ids"`
}

type FetchPrekeysResponse struct {
	Prekeys []string `json:"prekeys"`
}

func fetchPrekeys_restAPI_handler(user *User, req FetchPrekeysRequest) (FetchPrekeysResponse, *RestAPIError) {
	respChan := make(chan FetchPrekeyResponse)
	defer close(respChan)

	response := FetchPrekeysResponse{
		Prekeys: make([]string, 0, len(req.Ids)),
	}

	users := usersList.Load()
	for _, id := range req.Ids {
		fromId, err := userIDFromString(id)
		if err != nil {
			response.Prekeys = append(response.Prekeys, "")
			continue
		}
		fromUser := users.userById(fromId)
		if fromUser == nil {
			response.Prekeys = append(response.Prekeys, "")
			continue
		}

		fromUser.FetchPrekey <- FetchPrekeyRequest{
			Response: respChan,
		}
		resp := <-respChan

		response.Prekeys = append(response.Prekeys, resp.Prekey)
	}

	return response, nil
}

type FetchPrekeyRequest struct {
	Response chan<- FetchPrekeyResponse
}

type FetchPrekeyResponse struct {
	Prekey string
}

func fetchPrekey_synchronized_handler(user *User, req FetchPrekeyRequest) FetchPrekeyResponse {
	cnt := len(user.Prekeys)
	if cnt > 0 {
		fmt.Println("taking a prekey of ", user.Username)
		prekey := user.Prekeys[0]
		user.Prekeys = shift(user.Prekeys, 1)
		_ = user.save_user()

		return FetchPrekeyResponse{
			Prekey: prekey,
		}
	} else {
		fmt.Println("no prekeys for ", user.Username)
		return FetchPrekeyResponse{
			Prekey: "",
		}
	}
}
