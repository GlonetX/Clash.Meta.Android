// Package backup contains side-effect-free, streaming backup primitives. It
// never changes the running core's configuration or global subscription keys.
package backup

import (
	"bufio"
	"bytes"
	"errors"
	"io"
	"os"
	"strings"
	"unicode/utf8"

	"github.com/metacubex/age"
)

const MaxArchive = int64(256 << 20)
const MaxHeader = 64 << 10
const WorkFactor = 16

func validPassword(password []byte) error {
	if !utf8.Valid(password) || utf8.RuneCount(password) < 16 || len(password) > 1024 {
		return errors.New("backup password must contain at least 16 characters and at most 1024 UTF-8 bytes")
	}
	return nil
}

// CryptFile is intentionally file-backed: heap use does not grow with archive
// size. A failed operation removes its output, never the input.
func CryptFile(input, output string, password []byte, decrypt bool) (err error) {
	if err = validPassword(password); err != nil {
		return err
	}
	if input == output {
		return errors.New("input and output must differ")
	}
	src, err := os.Open(input)
	if err != nil {
		return err
	}
	defer src.Close()
	stat, err := src.Stat()
	if err != nil {
		return err
	}
	if stat.Size() > MaxArchive {
		return errors.New("backup exceeds 256 MiB transfer limit")
	}
	dst, err := os.OpenFile(output, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		return err
	}
	defer func() {
		if closeErr := dst.Close(); err == nil {
			err = closeErr
		}
		if err != nil {
			_ = os.Remove(output)
		}
	}()
	if decrypt {
		// Bound the entire attacker-controlled age header before running any KDF.
		reader := bufio.NewReaderSize(src, MaxHeader)
		header := make([]byte, 0, 1024)
		for {
			line, e := reader.ReadSlice('\n')
			if e != nil {
				return errors.New("invalid or truncated age header")
			}
			header = append(header, line...)
			if len(header) > MaxHeader {
				return errors.New("age header exceeds 64 KiB")
			}
			if bytes.HasPrefix(line, []byte("--- ")) {
				break
			}
		}
		if !strings.HasPrefix(string(header), "age-encryption.org/v1\n") {
			return errors.New("unsupported encrypted backup format")
		}
		stanzaCount := 0
		for _, line := range strings.Split(string(header), "\n") {
			if strings.HasPrefix(line, "-> ") {
				stanzaCount++
				fields := strings.Fields(line)
				if len(fields) != 4 || fields[1] != "scrypt" || fields[3] != "16" {
					return errors.New("backup v1 requires a single scrypt recipient with work factor 16")
				}
			}
		}
		if stanzaCount != 1 {
			return errors.New("invalid backup recipient count")
		}
		identity, e := age.NewScryptIdentity(string(password))
		if e != nil {
			return e
		}
		identity.SetMaxWorkFactor(WorkFactor)
		plain, e := age.Decrypt(io.MultiReader(strings.NewReader(string(header)), reader), identity)
		if e != nil {
			return errors.New("incorrect password or damaged encrypted backup")
		}
		n, e := io.Copy(dst, io.LimitReader(plain, MaxArchive+1))
		if e != nil {
			return errors.New("encrypted backup authentication failed")
		}
		if n > MaxArchive {
			return errors.New("decrypted archive exceeds 256 MiB")
		}
		// Copy read through authenticated EOF, not merely the last ZIP entry.
	} else {
		recipient, e := age.NewScryptRecipient(string(password))
		if e != nil {
			return e
		}
		recipient.SetWorkFactor(WorkFactor)
		writer, e := age.Encrypt(&limitedWriter{dst: dst, left: MaxArchive}, recipient)
		if e != nil {
			return e
		}
		if _, e = io.Copy(writer, src); e != nil {
			return e
		}
		if e = writer.Close(); e != nil {
			return e
		}
	}
	return dst.Sync()
}

type limitedWriter struct {
	dst  io.Writer
	left int64
}

func (w *limitedWriter) Write(p []byte) (int, error) {
	if int64(len(p)) > w.left {
		return 0, errors.New("encrypted backup exceeds 256 MiB")
	}
	n, e := w.dst.Write(p)
	w.left -= int64(n)
	return n, e
}
