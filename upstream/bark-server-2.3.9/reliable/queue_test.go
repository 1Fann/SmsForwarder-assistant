package reliable

import (
	"errors"
	bolt "go.etcd.io/bbolt"
	"path/filepath"
	"testing"
	"time"
)

func openTest(t *testing.T, path string) *Queue {
	t.Helper()
	q, e := Open(path)
	if e != nil {
		t.Fatal(e)
	}
	return q
}
func TestDurableAcceptanceAndProviderDedupe(t *testing.T) {
	path := filepath.Join(t.TempDir(), "q.db")
	now := time.Unix(10000, 0)
	q := openTest(t, path)
	id, e := q.SubmitOnce([]byte(`{"body":"same"}`), "sms:device:1", now)
	if e != nil {
		t.Fatal(e)
	}
	q.Close()
	q = openTest(t, path)
	defer q.Close()
	again, e := q.SubmitOnce([]byte(`{"body":"same"}`), "sms:device:1", now)
	if e != nil || again != id {
		t.Fatal(again, e)
	}
	calls := 0
	q.Step(now, func(item Item) (int, error) {
		calls++
		if item.ID != id {
			t.Fatal(item.ID)
		}
		return 200, nil
	})
	q.SubmitOnce([]byte(`{"body":"same"}`), "sms:device:1", now)
	q.Step(now, func(Item) (int, error) { calls++; return 200, nil })
	if calls != 1 {
		t.Fatal("replayed acknowledged provider ID")
	}
	q.SubmitOnce([]byte(`{"body":"same"}`), "sms:device:2", now)
	q.Step(now, func(Item) (int, error) { calls++; return 200, nil })
	if calls != 2 {
		t.Fatal("distinct identical SMS suppressed")
	}
}
func TestRetrySurvivesRestartAndContinuesBeyondFiveAttempts(t *testing.T) {
	path := filepath.Join(t.TempDir(), "q.db")
	q := openTest(t, path)
	now := time.Unix(10000, 0)
	q.Submit([]byte(`{"body":"x"}`), now)
	for i := 0; i < 8; i++ {
		worked, e := q.Step(now, func(Item) (int, error) { return 503, errors.New("offline") })
		if !worked || e != nil {
			t.Fatal(worked, e)
		}
		q.Close()
		q = openTest(t, path)
		now = now.Add(301 * time.Second)
	}
	defer q.Close()
	s, e := q.Status()
	if e != nil || s.Pending != 1 || s.Retrying != 1 {
		t.Fatal(s, e)
	}
	q.Step(now, func(item Item) (int, error) {
		if item.Attempts != 8 {
			t.Fatal(item.Attempts)
		}
		return 200, nil
	})
	s, _ = q.Status()
	if s.Pending != 0 {
		t.Fatal(s)
	}
}
func TestClockRollbackAndHealthyMessagesAreNotBlocked(t *testing.T) {
	q := openTest(t, filepath.Join(t.TempDir(), "q.db"))
	defer q.Close()
	now := time.Unix(10000, 0)
	q.Submit([]byte(`{"body":"bad"}`), now)
	q.Step(now, func(Item) (int, error) { return 400, errors.New("token") })
	q.Submit([]byte(`{"body":"good"}`), now)
	worked, e := q.Step(now, func(Item) (int, error) { return 200, nil })
	if !worked || e != nil {
		t.Fatal(worked, e)
	}
	worked, e = q.Step(now.Add(-time.Hour), func(Item) (int, error) { return 200, nil })
	if !worked || e != nil {
		t.Fatal("clock rollback blocked", e)
	}
}
func TestFailedPersistenceDoesNotAcknowledge(t *testing.T) {
	q := openTest(t, filepath.Join(t.TempDir(), "q.db"))
	q.Close()
	if _, err := q.Submit([]byte(`{"body":"x"}`), time.Now()); err == nil {
		t.Fatal("closed storage accepted work")
	}
}
func TestDamagedRecordIsPreservedWithoutBlockingOtherNotifications(t *testing.T) {
	q := openTest(t, filepath.Join(t.TempDir(), "q.db"))
	defer q.Close()
	q.db.Update(func(tx *bolt.Tx) error { return tx.Bucket(bucket).Put([]byte("broken"), []byte("invalid JSON")) })
	q.Submit([]byte(`{"body":"healthy"}`), time.Now())
	worked, err := q.Step(time.Now(), func(Item) (int, error) { return 200, nil })
	if !worked || err != nil {
		t.Fatal(worked, err)
	}
	s, err := q.Status()
	if err != nil || s.Pending != 1 || s.Blocked != 1 {
		t.Fatal(s, err)
	}
	q.db.View(func(tx *bolt.Tx) error {
		if string(tx.Bucket(quarantineBucket).Get([]byte("broken"))) != "invalid JSON" {
			t.Fatal("damaged payload discarded")
		}
		return nil
	})
}
