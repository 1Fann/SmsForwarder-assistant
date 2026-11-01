package main

import (
	"bytes"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"

	"github.com/finb/bark-server/v2/database"
	"github.com/finb/bark-server/v2/reliable"
	"github.com/gofiber/fiber/v2"
)

// Run with the production .go files plus this test, excluding upstream's live
// APNs push_test.go. All sends here use an injected local callback.
func TestProviderIngressDurability(t *testing.T) {
	var err error
	androidQueue, err = reliable.Open(filepath.Join(t.TempDir(), "q.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer func() { androidQueue.Close(); androidQueue = nil }()
	db = database.NewMemBase()
	key, err := db.SaveDeviceTokenByKey("MemoryBaseKey", "fake-test-token")
	if err != nil {
		t.Fatal(err)
	}
	app := fiber.New()
	for _, route := range routes {
		if route.Name == "delivery_status" {
			route.Func(app)
		}
	}
	send := func(id string, want int) {
		request := httptest.NewRequest("POST", "/reliable-sms", bytes.NewBufferString(`{"request_id":"`+id+`","device_key":"`+key+`","body":"fixture"}`))
		request.Header.Set("Content-Type", "application/json")
		response, e := app.Test(request)
		if e != nil {
			t.Fatal(e)
		}
		response.Body.Close()
		if response.StatusCode != want {
			t.Fatal(response.StatusCode)
		}
	}
	id := "sms:0123456789abcdef0123456789abcdef:42"
	send(id, 200)
	send(id, 200)
	status, _ := androidQueue.Status()
	if status.Pending != 1 {
		t.Fatal(status)
	}
	androidQueue.Step(time.Now(), func(reliable.Item) (int, error) { return 200, nil })
	send(id, 200)
	status, _ = androidQueue.Status()
	if status.Pending != 0 {
		t.Fatal("acknowledged provider message replayed")
	}
	send("not-a-provider-id", 400)
	androidQueue.Close()
	send("sms:0123456789abcdef0123456789abcdef:43", 503)
}
