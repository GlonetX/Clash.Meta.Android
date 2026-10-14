package tunnel

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	clashHttp "github.com/metacubex/mihomo/component/http"
)

const (
	ipQualityTimeout  = 15 * time.Second
	ipQualityMaxBody  = 1 << 20
	ipQualityEndpoint = "https://ipinfo.check.place"
)

// IPQuality holds the aggregated risk info of an IP from multiple databases.
type IPQuality struct {
	IP  string `json:"ip"`
	ASN string `json:"asn,omitempty"`

	ScamalyticsScore    string `json:"scamalyticsScore,omitempty"`
	ScamalyticsRisk     string `json:"scamalyticsRisk,omitempty"`
	IPQSFraudScore      string `json:"ipqsFraudScore,omitempty"`
	IPQSProxy           string `json:"ipqsProxy,omitempty"`
	IPQSVpn             string `json:"ipqsVpn,omitempty"`
	AbuseConfidence     string `json:"abuseConfidence,omitempty"`
	AbuseUsageType      string `json:"abuseUsageType,omitempty"`
	IP2LocationProxy    string `json:"ip2locationProxy,omitempty"`
	IP2LocationUsage    string `json:"ip2locationUsage,omitempty"`
}

func fetchIPQuality(ctx context.Context, url string) (map[string]any, error) {
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet,
		http.Header{"User-Agent": {"clash.meta"}}, nil)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d", response.StatusCode)
	}

	body, err := io.ReadAll(io.LimitReader(response.Body, ipQualityMaxBody))
	if err != nil {
		return nil, err
	}

	var result map[string]any
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, err
	}

	return result, nil
}

func stringOf(m map[string]any, key string) string {
	if m == nil {
		return ""
	}

	if v, ok := m[key]; ok {
		switch value := v.(type) {
		case string:
			return value
		case float64:
			return fmt.Sprintf("%.0f", value)
		case bool:
			if value {
				return "true"
			}
			return "false"
		}
	}

	return ""
}

// QueryIPQuality queries aggregated IP intelligence for the given IP.
// The requests go through the tunnel, so they inspect the exit IP.
func QueryIPQuality(ip string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), ipQualityTimeout)
	defer cancel()

	var (
		wg         sync.WaitGroup
		scam       map[string]any
		ipqs       map[string]any
		abuse      map[string]any
		ip2loc     map[string]any
		scamErr    error
		ipqsErr    error
		abuseErr   error
		ip2locErr  error
	)

	target := ip
	if target == "" {
		target = "ip"
	}

	fetch := func(url string, result *map[string]any, errOut *error) {
		defer wg.Done()

		data, err := fetchIPQuality(ctx, url)
		if err != nil {
			*errOut = err
			return
		}

		*result = data
	}

	wg.Add(4)
	go fetch(ipQualityEndpoint+"/"+target+"?db=scamalytics", &scam, &scamErr)
	go fetch(ipQualityEndpoint+"/"+target+"?db=ipqualityscore", &ipqs, &ipqsErr)
	go fetch(ipQualityEndpoint+"/"+target+"?db=abuseipdb", &abuse, &abuseErr)
	go fetch(ipQualityEndpoint+"/"+target+"?db=ip2location", &ip2loc, &ip2locErr)
	wg.Wait()

	// at least one source must succeed
	if scam == nil && ipqs == nil && abuse == nil && ip2loc == nil {
		errs := []string{}
		for _, e := range []error{scamErr, ipqsErr, abuseErr, ip2locErr} {
			if e != nil {
				errs = append(errs, e.Error())
			}
		}
		return "", fmt.Errorf("all sources failed: %s", strings.Join(errs, "; "))
	}

	var quality IPQuality

	if scam != nil {
		quality.ScamalyticsScore = stringOf(scam, "score")
		quality.ScamalyticsRisk = stringOf(scam, "risk")
	}

	if ipqs != nil {
		if data, ok := ipqs["data"].(map[string]any); ok {
			quality.IPQSFraudScore = stringOf(data, "fraud_score")
			quality.IPQSProxy = stringOf(data, "proxy")
			quality.IPQSVpn = stringOf(data, "vpn")
		}
	}

	if abuse != nil {
		if data, ok := abuse["data"].(map[string]any); ok {
			quality.AbuseConfidence = stringOf(data, "abuseConfidenceScore")
			quality.AbuseUsageType = stringOf(data, "usageType")
		}
	}

	if ip2loc != nil {
		if data, ok := ip2loc["data"].(map[string]any); ok {
			quality.IP2LocationProxy = stringOf(data, "proxy_type")
			quality.IP2LocationUsage = stringOf(data, "usage_type")
		}
	}

	data, err := json.Marshal(quality)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

// TestLatency measures HTTP round-trip latency for the given URL through the tunnel.
// Returns latency in milliseconds; a negative value indicates failure.
func TestLatency(url string) int64 {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	start := time.Now()

	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet,
		http.Header{"User-Agent": {"clash.meta"}}, nil)
	if err != nil {
		return -1
	}
	defer response.Body.Close()
	io.Copy(io.Discard, io.LimitReader(response.Body, 4096))

	return time.Since(start).Milliseconds()
}
