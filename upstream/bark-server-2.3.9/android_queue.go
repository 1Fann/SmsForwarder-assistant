package main

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"time"

	"github.com/finb/bark-server/v2/reliable"
	"github.com/gofiber/fiber/v2"
	"github.com/mritd/logger"
)

var androidQueue *reliable.Queue

func init() {
	registerRoute("delivery_status", func(router fiber.Router) {
		router.Post("/reliable-sms", func(c *fiber.Ctx) error {
			if androidQueue == nil {
				return c.Status(503).JSON(failed(503, "reliable delivery unavailable"))
			}
			var params map[string]interface{}
			if err := c.BodyParser(&params); err != nil {
				return c.Status(400).JSON(failed(400, "invalid request"))
			}
			id, _ := params["request_id"].(string)
			if !regexp.MustCompile(`^sms:[a-f0-9]{32}:[0-9]{1,20}$`).MatchString(id) {
				return c.Status(400).JSON(failed(400, "invalid message identity"))
			}
			key, _ := params["device_key"].(string)
			if key == "" {
				return c.Status(400).JSON(failed(400, "device key missing"))
			}
			if _, err := db.DeviceTokenByKey(key); err != nil {
				return c.Status(400).JSON(failed(400, "device registration unavailable"))
			}
			delete(params, "request_id")
			payload, err := json.Marshal(params)
			if err != nil {
				return c.Status(400).JSON(failed(400, "invalid payload"))
			}
			if _, err = androidQueue.SubmitOnce(payload, id, time.Now()); err != nil {
				return c.Status(503).JSON(failed(503, "notification was not queued"))
			}
			return c.JSON(success())
		})
		router.Get("/delivery-status", func(c *fiber.Ctx) error {
			if androidQueue == nil {
				return c.Status(503).JSON(failed(503, "reliable delivery unavailable"))
			}
			status, err := androidQueue.Status()
			if err != nil {
				return c.Status(503).JSON(failed(503, "queue unavailable"))
			}
			return c.JSON(data(status))
		})
	})
}
func initializeAndroidQueue() error {
	dir := os.Getenv("BARK_ANDROID_STATE")
	if dir == "" {
		return nil
	}
	q, err := reliable.Open(filepath.Join(dir, "notifications.db"))
	if err != nil {
		return errors.New("cannot open durable notification queue")
	}
	androidQueue = q
	go func() {
		for {
			for i := 0; i < 32; i++ {
				worked, err := q.Step(time.Now(), func(item reliable.Item) (int, error) {
					var params map[string]interface{}
					if err := json.Unmarshal(item.Payload, &params); err != nil {
						return 400, err
					}
					if value, _ := params["id"].(string); value == "" {
						params["id"] = item.ID
					}
					return deliverPush(params)
				})
				if err != nil {
					logger.Error("notification queue storage unavailable")
					break
				}
				if !worked {
					break
				}
			}
			time.Sleep(2 * time.Second)
		}
	}()
	return nil
}
func push(params map[string]interface{}) (int, error) {
	if androidQueue == nil {
		return deliverPush(params)
	}
	key, _ := params["device_key"].(string)
	if key == "" {
		return 400, errors.New("device key is empty")
	}
	// Validate registration before accepting, but retain queued work if the token
	// later becomes unavailable. Registration repair can then resume delivery.
	if _, err := db.DeviceTokenByKey(key); err != nil {
		return 400, errors.New("device registration unavailable")
	}
	payload, err := json.Marshal(params)
	if err != nil {
		return 400, errors.New("invalid notification")
	}
	if _, err = androidQueue.Submit(payload, time.Now()); err != nil {
		return 503, errors.New("notification was not queued")
	}
	return 200, nil
}
