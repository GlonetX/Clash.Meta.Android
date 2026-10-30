package config

import (
	"encoding/json"
	"github.com/metacubex/mihomo/config"
	"sync"
)

type RuntimeSettings struct {
	KeepAliveEnabled  bool   `json:"keepAliveEnabled"`
	KeepAliveInterval int    `json:"keepAliveInterval"`
	KeepAliveIdle     int    `json:"keepAliveIdle"`
	StoreFakeIP       bool   `json:"storeFakeIp"`
	GlobalUserAgent   string `json:"globalUserAgent"`
	DownloadOnly      bool   `json:"downloadOnly"`
}

var runtimeSettingsMu sync.RWMutex
var runtimeSettings *RuntimeSettings
var subscriptionUA string

func SetRuntimeSettings(value string) {
	var next RuntimeSettings
	if json.Unmarshal([]byte(value), &next) != nil {
		return
	}
	runtimeSettingsMu.Lock()
	defer runtimeSettingsMu.Unlock()
	subscriptionUA = next.GlobalUserAgent
	if !next.DownloadOnly {
		runtimeSettings = &next
	}
}
func SubscriptionUserAgent(fallback string) string {
	runtimeSettingsMu.RLock()
	defer runtimeSettingsMu.RUnlock()
	if subscriptionUA != "" {
		return subscriptionUA
	}
	return fallback
}
func patchRuntimeSettings(cfg *config.RawConfig, _ string) error {
	runtimeSettingsMu.RLock()
	defer runtimeSettingsMu.RUnlock()
	if runtimeSettings == nil {
		cfg.Profile.StoreFakeIP = true
		return nil
	}
	cfg.DisableKeepAlive = !runtimeSettings.KeepAliveEnabled
	cfg.KeepAliveInterval = runtimeSettings.KeepAliveInterval
	cfg.KeepAliveIdle = runtimeSettings.KeepAliveIdle
	cfg.Profile.StoreFakeIP = runtimeSettings.StoreFakeIP
	if subscriptionUA != "" {
		cfg.GlobalUA = subscriptionUA
	}
	return nil
}
