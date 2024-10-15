package main

type UpdateCredRequest struct {
	Id      int64
	Token   []byte
	Key     []byte
	KeyHash []byte
}

var UpdateCred chan UpdateCredRequest = make(chan UpdateCredRequest)

func persistence(users_clone *Users) {
	for {
		select {
		case req := <-UpdateCred:
			user := users_clone.userById(req.Id)
			if user != nil {
				user.Token = req.Token
				user.Key = req.Key
				user.KeyHash = req.KeyHash

				users_clone.store()
			}
		}
	}
}
