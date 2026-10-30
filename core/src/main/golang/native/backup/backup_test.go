package backup

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestEncryptedRoundTripAndAuthentication(t *testing.T) {
	d := t.TempDir()
	src := filepath.Join(d, "source")
	enc := filepath.Join(d, "encrypted")
	out := filepath.Join(d, "plain")
	content := bytes.Repeat([]byte("private subscription data"), 10000)
	if e := os.WriteFile(src, content, 0600); e != nil {
		t.Fatal(e)
	}
	password := []byte("sixteen-characters-or-more")
	if e := CryptFile(src, enc, password, false); e != nil {
		t.Fatal(e)
	}
	if e := CryptFile(enc, out, password, true); e != nil {
		t.Fatal(e)
	}
	got, _ := os.ReadFile(out)
	if !bytes.Equal(got, content) {
		t.Fatal("round trip mismatch")
	}
	_ = os.Remove(out)
	if e := CryptFile(enc, out, []byte("a-different-long-password"), true); e == nil {
		t.Fatal("wrong password accepted")
	}
	if _, e := os.Stat(out); !os.IsNotExist(e) {
		t.Fatal("failed decrypt left plaintext")
	}
	encrypted, _ := os.ReadFile(enc)
	encrypted[len(encrypted)-1] ^= 1
	_ = os.WriteFile(enc, encrypted, 0600)
	if e := CryptFile(enc, out, password, true); e == nil {
		t.Fatal("tampered ciphertext accepted")
	}
}
func TestHeaderLimitsBeforeKDF(t *testing.T) {
	for name, data := range map[string]string{
		"oversize":  "age-encryption.org/v1\n" + strings.Repeat("x", MaxHeader+1),
		"expensive": "age-encryption.org/v1\n-> scrypt AAAAAAAAAAAAAAAAAAAAAA 22\nAAAA\n--- AAAAA\n",
	} {
		t.Run(name, func(t *testing.T) {
			d := t.TempDir()
			in := filepath.Join(d, "in")
			out := filepath.Join(d, "out")
			_ = os.WriteFile(in, []byte(data), 0600)
			if e := CryptFile(in, out, []byte("sixteen-characters-or-more"), true); e == nil {
				t.Fatal("unsafe header accepted")
			}
		})
	}
}
func TestProviderInventoryIsSemantic(t *testing.T) {
	d := t.TempDir()
	path := filepath.Join(d, "config.yaml")
	_ = os.WriteFile(path, []byte("proxy-providers:\n  remote:\n    type: http\n    path: hand-looking.yaml\n    url: https://example.invalid/sub\n  local:\n    type: file\n    path: local.yaml\n  inline:\n    type: inline\n    payload: []\n"), 0600)
	got, e := Inventory(path, "", "{}")
	if e != nil {
		t.Fatal(e)
	}
	if len(got.RemotePaths) != 1 || got.RemotePaths[0] != "providers/hand-looking.yaml" || len(got.LocalPaths) != 1 || got.LocalPaths[0] != "providers/local.yaml" {
		t.Fatalf("unexpected inventory: %+v", got)
	}
	_, e = Inventory(path, "", `{"proxy-providers":{"conflict":{"type":"file","path":"hand-looking.yaml"}}}`)
	if e == nil {
		t.Fatal("file/http path conflict accepted")
	}
}

func TestInventoryDetectsProviderURLsRegardlessOfType(t *testing.T) {
	for name, config := range map[string]string{
		"http loopback": "proxy-providers:\n  p:\n    type: http\n    url: http://127.0.0.1/x\n",
		"file content":  "rule-providers:\n  p:\n    type: file\n    path: rules.yaml\n    url: content://private/x\n",
		"inline url":    "proxy-providers:\n  p:\n    type: inline\n    url: https://example.com/x\n",
	} {
		t.Run(name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "config.yaml")
			if err := os.WriteFile(path, []byte(config), 0600); err != nil {
				t.Fatal(err)
			}
			inventory, err := Inventory(path, "", "{}")
			if err != nil {
				t.Fatal(err)
			}
			if !inventory.HasProviderURL {
				t.Fatal("provider URL was not detected")
			}
		})
	}
	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(path, []byte("proxies: []\n"), 0600); err != nil {
		t.Fatal(err)
	}
	got, err := Inventory(path, "", "{}")
	if err != nil || got.HasProviderURL {
		t.Fatalf("plain profile rejected: %+v, %v", got, err)
	}
	got, err = Inventory(path, "", `{"rule-providers":{"override":{"type":"http","url":"http://127.0.0.1/rules"}}}`)
	if err != nil || !got.HasProviderURL {
		t.Fatalf("override provider URL not detected: %+v, %v", got, err)
	}
}
