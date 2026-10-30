package backup

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"cfa/native/common"
	"github.com/metacubex/age"
	"github.com/metacubex/age/armor"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/common/yaml"
)

type ProviderInventory struct {
	RemotePaths    []string `json:"remotePaths"`
	LocalPaths     []string `json:"localPaths"`
	HasProviderURL bool     `json:"hasProviderUrl"`
}
type providerConfig struct {
	Proxies map[string]map[string]any `yaml:"proxy-providers" json:"proxy-providers"`
	Rules   map[string]map[string]any `yaml:"rule-providers" json:"rule-providers"`
}

// Inventory mirrors only the provider path transformation. It does not call
// ParseRawConfig (which mutates runtime state), fetch URLs, or use global keys.
func Inventory(configPath, secretKeys, override string) (ProviderInventory, error) {
	result := ProviderInventory{RemotePaths: []string{}, LocalPaths: []string{}}
	f, e := os.Open(configPath)
	if e != nil {
		return result, e
	}
	defer f.Close()
	data, e := io.ReadAll(io.LimitReader(f, (64<<20)+1))
	if e != nil {
		return result, e
	}
	if len(data) > 64<<20 {
		return result, errors.New("configuration too large")
	}
	if bytes.HasPrefix(data, []byte(armor.Header)) {
		identities, e := age.ParseIdentities(strings.NewReader(secretKeys))
		if e != nil {
			return result, errors.New("invalid profile decryption key")
		}
		plain, e := age.Decrypt(armor.NewReader(bytes.NewReader(data)), identities...)
		if e != nil {
			return result, errors.New("cannot decrypt profile for backup")
		}
		data, e = io.ReadAll(io.LimitReader(plain, (64<<20)+1))
		if e != nil {
			return result, e
		}
		if len(data) > 64<<20 {
			return result, errors.New("decrypted configuration too large")
		}
	}
	cfg := providerConfig{}
	if e = yaml.Unmarshal(data, &cfg); e != nil {
		return result, errors.New("cannot parse profile for backup")
	}
	if strings.TrimSpace(override) != "" {
		if e = json.Unmarshal([]byte(override), &cfg); e != nil {
			return result, errors.New("cannot parse persistent override")
		}
	}
	kinds := map[string]string{}
	for prefix, providers := range map[string]map[string]map[string]any{"proxies": cfg.Proxies, "rules": cfg.Rules} {
		for name, p := range providers {
			if _, ok := p["url"]; ok {
				result.HasProviderURL = true
			}
			typ, _ := p["type"].(string)
			if typ == "inline" {
				continue
			}
			if typ != "file" && typ != "http" {
				return result, fmt.Errorf("unsupported provider type for %s", name)
			}
			path, _ := p["path"].(string)
			if path != "" {
				path = common.ResolveAsRoot(path)
			} else if url, ok := p["url"].(string); ok && url != "" {
				path = prefix + "/" + utils.MakeHash([]byte(url)).String()
			}
			if path == "" || filepath.IsAbs(path) || strings.Contains(path, "\\") {
				return result, fmt.Errorf("invalid provider path for %s", name)
			}
			path = "providers/" + path
			if old, ok := kinds[path]; ok && old != typ {
				return result, errors.New("local and HTTP providers share a path")
			}
			kinds[path] = typ
		}
	}
	for path, kind := range kinds {
		if kind == "http" {
			result.RemotePaths = append(result.RemotePaths, path)
		} else {
			result.LocalPaths = append(result.LocalPaths, path)
		}
	}
	sort.Strings(result.RemotePaths)
	sort.Strings(result.LocalPaths)
	return result, nil
}
