package main

import (
	"encoding/json"
	"os"
	"sort"
)

type UpdateCredRequest struct {
	Id     UserID
	Bearer Bearer
	Key    string
}

var UpdateCred chan UpdateCredRequest = make(chan UpdateCredRequest)

func persistence(users_clone *Users) {
	for {
		select {
		case req := <-UpdateCred:
			user := users_clone.userById(req.Id)
			if user != nil {
				user.Bearer = req.Bearer
				user.Key = req.Key

				users_clone.store()
			}
		}
	}
}

func (users *Users) store() error {
	jsonData, err := func() ([]byte, error) {
		users_vec := make([]*User, 0, len(users.name_map))
		for _, user := range users.name_map {
			users_vec = append(users_vec, user)
		}
		sort.Slice(users_vec, func(i, j int) bool {
			return users_vec[i].Username < users_vec[j].Username
		})

		return json.MarshalIndent(users_vec, "", "\t")
	}()
	if err != nil {
		return err
	}

	jsonData = append(jsonData, '\n')
	err = os.WriteFile(users.filename, jsonData, 0644)
	if err != nil {
		return err
	}

	return nil
}
