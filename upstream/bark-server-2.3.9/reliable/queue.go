// Package reliable stores accepted notifications before acknowledging them.
package reliable

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"time"

	bolt "go.etcd.io/bbolt"
)

var bucket = []byte("pending")
var seenBucket = []byte("accepted_ids")
var quarantineBucket = []byte("quarantined")

const MaxPending = 10000

type Item struct {
	ID       string
	Payload  json.RawMessage
	Created  int64
	Next     int64
	Attempts int
	LastCode int
}
type Status struct {
	Pending  int   `json:"pending"`
	Retrying int   `json:"retrying"`
	Oldest   int64 `json:"oldest"`
	Blocked  int   `json:"blocked"`
}
type Queue struct{ db *bolt.DB }

func Open(path string) (*Queue, error) {
	db, err := bolt.Open(path, 0600, &bolt.Options{Timeout: 2 * time.Second})
	if err != nil {
		return nil, err
	}
	q := &Queue{db: db}
	if err = db.Update(func(tx *bolt.Tx) error {
		if _, err := tx.CreateBucketIfNotExists(bucket); err != nil {
			return err
		}
		if _, err := tx.CreateBucketIfNotExists(seenBucket); err != nil {
			return err
		}
		_, err := tx.CreateBucketIfNotExists(quarantineBucket)
		return err
	}); err != nil {
		db.Close()
		return nil, err
	}
	return q, nil
}
func (q *Queue) Close() error { return q.db.Close() }
func (q *Queue) Submit(payload []byte, now time.Time) (string, error) {
	return q.SubmitOnce(payload, "", now)
}
func (q *Queue) SubmitOnce(payload []byte, sourceID string, now time.Time) (string, error) {
	if !json.Valid(payload) || len(payload) > 65536 {
		return "", errors.New("invalid notification payload")
	}
	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	id := hex.EncodeToString(raw)
	if sourceID != "" {
		sum := sha256.Sum256([]byte(sourceID))
		id = hex.EncodeToString(sum[:16])
	}
	item := Item{ID: id, Payload: append([]byte(nil), payload...), Created: now.Unix()}
	data, err := json.Marshal(item)
	if err != nil {
		return "", err
	}
	err = q.db.Update(func(tx *bolt.Tx) error {
		seen := tx.Bucket(seenBucket)
		if sourceID != "" && seen.Get([]byte(id)) != nil {
			return nil
		}
		b := tx.Bucket(bucket)
		if b.Stats().KeyN >= MaxPending {
			return errors.New("notification queue is full")
		}
		if err := b.Put([]byte(id), data); err != nil {
			return err
		}
		if sourceID != "" {
			return seen.Put([]byte(id), []byte{1})
		}
		return nil
	})
	return id, err
}

// Step has one owner. Network I/O happens outside the transaction. A crash after
// remote acceptance but before Delete may repeat delivery; callers should reuse
// the queue ID as their APNs collapse ID. This is at-least-once, not exactly-once.
func (q *Queue) Step(now time.Time, send func(Item) (int, error)) (bool, error) {
	var selected *Item
	var damaged [][]byte
	err := q.db.View(func(tx *bolt.Tx) error {
		return tx.Bucket(bucket).ForEach(func(k, v []byte) error {
			var item Item
			if err := json.Unmarshal(v, &item); err != nil || item.ID != string(k) || !json.Valid(item.Payload) {
				damaged = append(damaged, append([]byte(nil), k...))
				return nil
			}
			// Clock rollback must not postpone retry by hours or days.
			if item.Next > now.Unix() && item.Next <= now.Unix()+300 {
				return nil
			}
			if selected == nil || item.Next < selected.Next {
				copy := item
				selected = &copy
			}
			return nil
		})
	})
	if err == nil && len(damaged) > 0 {
		err = q.db.Update(func(tx *bolt.Tx) error {
			pending := tx.Bucket(bucket)
			quarantine := tx.Bucket(quarantineBucket)
			for _, key := range damaged {
				if raw := pending.Get(key); raw != nil {
					if e := quarantine.Put(key, raw); e != nil {
						return e
					}
					if e := pending.Delete(key); e != nil {
						return e
					}
				}
			}
			return nil
		})
	}
	if err != nil || selected == nil {
		return false, err
	}
	code, sendErr := send(*selected)
	err = q.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(bucket)
		if sendErr == nil && code == 200 {
			return b.Delete([]byte(selected.ID))
		}
		selected.Attempts++
		selected.LastCode = code
		delay := 5 * time.Second
		for i := 1; i < selected.Attempts && delay < 300*time.Second; i++ {
			delay *= 2
		}
		if delay > 300*time.Second {
			delay = 300 * time.Second
		}
		selected.Next = now.Add(delay).Unix()
		data, e := json.Marshal(selected)
		if e != nil {
			return e
		}
		return b.Put([]byte(selected.ID), data)
	})
	return true, err
}
func (q *Queue) Status() (Status, error) {
	var s Status
	err := q.db.View(func(tx *bolt.Tx) error {
		s.Blocked = tx.Bucket(quarantineBucket).Stats().KeyN
		s.Pending = s.Blocked
		return tx.Bucket(bucket).ForEach(func(k, v []byte) error {
			var item Item
			s.Pending++
			if err := json.Unmarshal(v, &item); err != nil || item.ID != string(k) || !json.Valid(item.Payload) {
				s.Blocked++
				return nil
			}
			if item.Attempts > 0 {
				s.Retrying++
			}
			if s.Oldest == 0 || item.Created < s.Oldest {
				s.Oldest = item.Created
			}
			return nil
		})
	})
	return s, err
}
