package main

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type Message struct {
	// When stored in the inbox, `Time` is seconds since `referenceTime`
	// When sent over the network, `Time` is the age of this message in seconds (i.e., `now - Time`)
	Time int64  `json:"ageSec"`
	From string `json:"from"`
	Type string `json:"type"`
	Msg  string `json:"msg"`
}

type Inbox struct {
	User  *User
	Parts []InboxPart
}

func newInbox(user *User) Inbox {
	return Inbox{
		User:  user,
		Parts: make([]InboxPart, 0),
	}
}

func (inbox *Inbox) removeExpiredParts(now int64) {
	// Note that inbox parts are sorted by their start time
	firstNotExpired := find_first(
		inbox.Parts,
		func(p InboxPart) bool { return p.canContainUnexpiredMessages(now) },
	)
	for i := 0; i < firstNotExpired; i++ {
		// Ignoring errors here. Not sure I can do anything about them.
		_ = os.Remove(inbox.Parts[i].File)
	}
	inbox.Parts = shift(inbox.Parts, firstNotExpired)
}

func (inbox *Inbox) clear() {
	for _, part := range inbox.Parts {
		_ = os.Remove(part.File)
	}
	inbox.Parts = inbox.Parts[:0]
}

func (inbox *Inbox) addPart(now int64) *InboxPart {
	part := newInboxPart(inbox.User, now)
	inbox.Parts = append(inbox.Parts, part)
	return &inbox.Parts[len(inbox.Parts)-1]
}

func (inbox *Inbox) getPart(now int64) *InboxPart {
	partCnt := len(inbox.Parts)
	if partCnt > 0 && inbox.Parts[partCnt-1].canAdd(now) {
		// We have an inbox part that's not too old, so we can use it
		return &inbox.Parts[partCnt-1]
	} else {
		// We either don't have any inbox parts, or the last one is too old.
		// Create a new one
		inbox.removeExpiredParts(now)
		return inbox.addPart(now)
	}
}

// The `Time` field of the message should contain seconds since `referenceTime`, i.e. `now`.
// I.e., it is ready to be stored in the inbox.
func (inbox *Inbox) addMessage(msg *Message) *RestAPIError {
	// Make sure `Type` and `Data` don't contain any space or newline.
	// We use these characters as separators in the inbox file format.
	if strings.ContainsAny(msg.Type, " \n") || strings.ContainsAny(msg.Msg, " \n") {
		return NewError("invalid message", http.StatusBadRequest)
	}

	// Get usable inbox part or create a new one
	inboxPart := inbox.getPart(msg.Time)

	// Write the message to the inbox part
	Log.i("Writing message to file: %s", inboxPart.File)
	f, err := os.OpenFile(inboxPart.File, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return NewError("opening file: "+err.Error(), http.StatusInternalServerError)
	}
	defer f.Close()

	_, err = f.WriteString(fmt.Sprintf("%d %s %s %s\n", msg.Time, msg.From, msg.Type, msg.Msg))
	if err != nil {
		return NewError("writing to file: "+err.Error(), http.StatusInternalServerError)
	}

	return nil
}

func (part *InboxPart) getMessages(now int64, messages []Message) []Message {
	f, err := os.Open(part.File)
	if err != nil {
		Log.e("Error opening file: %s", err.Error())
		return messages
	}
	defer f.Close()

	validRange := timeRange(now-MSG_EXPIRE_SEC, MSG_EXPIRE_SEC+10)

	for {
		var msg Message
		_, err := fmt.Fscanf(f, "%d %s %s %s\n", &msg.Time, &msg.From, &msg.Type, &msg.Msg)
		if err != nil {
			if err.Error() != "EOF" {
				Log.e("Error reading message: %s", err.Error())
			}
			break
		}
		if validRange.contains(msg.Time) {
			msg.Time = now - msg.Time
			messages = append(messages, msg)
		}
	}
	return messages
}

// The `Time` field of the returned messages contains the age of the message in seconds.
// I.e., they are ready to be sent over the network.
func (inbox *Inbox) getMessages(now int64) ([]Message, *RestAPIError) {
	messages := make([]Message, 0)
	partCnt := len(inbox.Parts)
	if partCnt == 0 {
		return messages, nil
	}

	for i, part := range inbox.Parts[:partCnt-1] {
		if part.canContainUnexpiredMessages(now) {
			messages = part.getMessages(now, messages)
		} else {
			Log.i("Not using expired inbox part: %s", part.File)
		}
		_ = os.Remove(part.File)
		inbox.Parts[i] = InboxPart{}
	}

	currentPart := inbox.Parts[partCnt-1]
	inbox.Parts[partCnt-1] = InboxPart{}
	inbox.Parts = inbox.Parts[:0]

	if currentPart.canContainUnexpiredMessages(now) {
		messages = currentPart.getMessages(now, messages)
	} else {
		Log.i("Not using expired inbox part: %s", currentPart.File)
	}
	truncated := false
	if currentPart.canAdd(now) {
		err := os.Truncate(currentPart.File, 0)
		truncated = (err == nil)
	}
	if truncated {
		inbox.Parts = append(inbox.Parts, currentPart)
	} else {
		_ = os.Remove(currentPart.File)
	}

	return messages, nil
}

type InboxPart struct {
	FirstMessageTimestamp int64
	File                  string
}

func newInboxPart(user *User, now int64) InboxPart {
	return InboxPart{
		FirstMessageTimestamp: now,
		File:                  inboxFile(user.inboxDir(), now),
	}
}

func (p *InboxPart) canAdd(now int64) bool {
	return timeRange(p.FirstMessageTimestamp, SWITCH_INBOX_SEC).contains(now)
}

func (p *InboxPart) canContainUnexpiredMessages(now int64) bool {
	msgValidRange := timeRange(now-MSG_EXPIRE_SEC, MSG_EXPIRE_SEC+10)
	partRange := timeRange(p.FirstMessageTimestamp, SWITCH_INBOX_SEC+1)
	return partRange.overlaps(msgValidRange)
}

func inboxFile(dir string, time int64) string {
	return filepath.Join(dir, fmt.Sprintf("%020d", time))
}

func (user *User) loadInbox() error {
	user.Inbox = newInbox(user)

	inboxDir := user.inboxDir()
	if err := os.MkdirAll(inboxDir, 0755); err != nil {
		return err
	}

	files, err := filepath.Glob(filepath.Join(inboxDir, "*"))
	if err != nil {
		return err
	}

	for _, file := range files {
		timestampStr := filepath.Base(file)
		timestamp, err := strconv.ParseInt(timestampStr, 10, 64)
		if err != nil {
			continue
		}

		user.Inbox.Parts = append(user.Inbox.Parts, InboxPart{
			FirstMessageTimestamp: timestamp,
			File:                  file,
		})
	}

	return nil
}
