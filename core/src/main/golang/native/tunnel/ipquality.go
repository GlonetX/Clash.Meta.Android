package tunnel

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"

	clashHttp "github.com/metacubex/mihomo/component/http"
)

const (
	ipQualityTimeout = 25 * time.Second
	ipQualityMaxBody = 1 << 20
	ipEchoURL        = "https://api.ip.sb/ip"
)

// browserUserAgent avoids naive bot filtering on some endpoints
const browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

// IPQuality holds the aggregated risk info of an IP from multiple databases.
type IPQuality struct {
	IP  string `json:"ip"`
	ASN string `json:"asn,omitempty"`

	ScamalyticsScore string `json:"scamalyticsScore,omitempty"`
	ScamalyticsRisk  string `json:"scamalyticsRisk,omitempty"`
	ProxyCheckRisk   string `json:"proxyCheckRisk,omitempty"`
	ProxyCheckProxy  string `json:"proxyCheckProxy,omitempty"`
	ProxyCheckType   string `json:"proxyCheckType,omitempty"`
	IPApiProxy       string `json:"ipApiProxy,omitempty"`
	IPApiHosting     string `json:"ipApiHosting,omitempty"`
	IPApiMobile      string `json:"ipApiMobile,omitempty"`
	IPApiISP         string `json:"ipApiISP,omitempty"`
}

var (
	scamalyticsScoreRe = regexp.MustCompile(`Fraud Score:\s*(\d+)`)
	scamalyticsRiskRe  = regexp.MustCompile(`>\s*(Low|Medium|High|Very High)\s+Risk\s*<`)
)

func httpGet(ctx context.Context, url string) ([]byte, int, error) {
	response, err := clashHttp.HttpRequest(ctx, url, http.MethodGet,
		http.Header{"User-Agent": {browserUserAgent}}, nil)
	if err != nil {
		return nil, 0, err
	}
	defer response.Body.Close()

	body, err := io.ReadAll(io.LimitReader(response.Body, ipQualityMaxBody))
	if err != nil {
		return nil, response.StatusCode, err
	}

	return body, response.StatusCode, nil
}

func boolText(v bool) string {
	if v {
		return "true"
	}
	return "false"
}

// resolveExitIP fetches the current exit IP as seen through the tunnel.
func resolveExitIP(ctx context.Context) string {
	body, status, err := httpGet(ctx, ipEchoURL)
	if err != nil || status != http.StatusOK {
		return ""
	}

	return strings.TrimSpace(string(body))
}

type scamalyticsResult struct {
	score string
	risk  string
}

func fetchScamalytics(ctx context.Context, ip string) (scamalyticsResult, error) {
	body, status, err := httpGet(ctx, "https://scamalytics.com/ip/"+ip)
	if err != nil {
		return scamalyticsResult{}, err
	}

	if status != http.StatusOK {
		return scamalyticsResult{}, fmt.Errorf("scamalytics: status %d", status)
	}

	html := string(body)

	result := scamalyticsResult{}

	if m := scamalyticsScoreRe.FindStringSubmatch(html); m != nil {
		result.score = m[1]
	}

	if m := scamalyticsRiskRe.FindStringSubmatch(html); m != nil {
		result.risk = m[1] + " Risk"
	}

	if result.score == "" && result.risk == "" {
		return scamalyticsResult{}, fmt.Errorf("scamalytics: parse failed")
	}

	return result, nil
}

type proxyCheckResult struct {
	risk  string
	proxy string
	typ   string
}

func fetchProxyCheck(ctx context.Context, ip string) (proxyCheckResult, error) {
	body, status, err := httpGet(ctx, "https://proxycheck.io/v2/"+ip+"?vpn=1&asn=1&risk=1")
	if err != nil {
		return proxyCheckResult{}, err
	}

	if status != http.StatusOK {
		return proxyCheckResult{}, fmt.Errorf("proxycheck: status %d", status)
	}

	var outer map[string]json.RawMessage
	if err := json.Unmarshal(body, &outer); err != nil {
		return proxyCheckResult{}, err
	}

	entry, ok := outer[ip]
	if !ok {
		return proxyCheckResult{}, fmt.Errorf("proxycheck: no entry")
	}

	var info struct {
		Proxy string      `json:"proxy"`
		Type  string      `json:"type"`
		Risk  json.Number `json:"risk"`
	}
	if err := json.Unmarshal(entry, &info); err != nil {
		return proxyCheckResult{}, err
	}

	return proxyCheckResult{
		risk:  info.Risk.String(),
		proxy: info.Proxy,
		typ:   info.Type,
	}, nil
}

type ipApiResult struct {
	proxy   string
	hosting string
	mobile  string
	as      string
	isp     string
}

func fetchIPApi(ctx context.Context, ip string) (ipApiResult, error) {
	body, status, err := httpGet(ctx, "http://ip-api.com/json/"+ip+"?fields=status,message,isp,as,mobile,proxy,hosting")
	if err != nil {
		return ipApiResult{}, err
	}

	if status != http.StatusOK {
		return ipApiResult{}, fmt.Errorf("ip-api: status %d", status)
	}

	var info struct {
		Status  string `json:"status"`
		Message string `json:"message"`
		Proxy   bool   `json:"proxy"`
		Hosting bool   `json:"hosting"`
		Mobile  bool   `json:"mobile"`
		As      string `json:"as"`
		Isp     string `json:"isp"`
	}
	if err := json.Unmarshal(body, &info); err != nil {
		return ipApiResult{}, err
	}

	if info.Status != "success" {
		return ipApiResult{}, fmt.Errorf("ip-api: %s", info.Message)
	}

	return ipApiResult{
		proxy:   boolText(info.Proxy),
		hosting: boolText(info.Hosting),
		mobile:  boolText(info.Mobile),
		as:      info.As,
		isp:     info.Isp,
	}, nil
}

// QueryIPQuality queries aggregated IP intelligence for the given IP.
// An empty ip resolves the current exit IP through the tunnel first.
func QueryIPQuality(ip string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), ipQualityTimeout)
	defer cancel()

	target := strings.TrimSpace(ip)
	if target == "" {
		target = resolveExitIP(ctx)
		if target == "" {
			return "", fmt.Errorf("failed to resolve exit ip")
		}
	}

	var (
		wg                        sync.WaitGroup
		scam                      scamalyticsResult
		check                     proxyCheckResult
		api                       ipApiResult
		scamErr, checkErr, apiErr error
	)

	wg.Add(3)
	go func() {
		defer wg.Done()
		scam, scamErr = fetchScamalytics(ctx, target)
	}()
	go func() {
		defer wg.Done()
		check, checkErr = fetchProxyCheck(ctx, target)
	}()
	go func() {
		defer wg.Done()
		api, apiErr = fetchIPApi(ctx, target)
	}()
	wg.Wait()

	// at least one source must succeed
	if scamErr != nil && checkErr != nil && apiErr != nil {
		errs := []string{scamErr.Error(), checkErr.Error(), apiErr.Error()}
		return "", fmt.Errorf("all sources failed: %s", strings.Join(errs, "; "))
	}

	quality := IPQuality{IP: target}

	if scamErr == nil {
		quality.ScamalyticsScore = scam.score
		quality.ScamalyticsRisk = scam.risk
	}

	if checkErr == nil {
		quality.ProxyCheckRisk = check.risk
		quality.ProxyCheckProxy = check.proxy
		quality.ProxyCheckType = check.typ
	}

	if apiErr == nil {
		quality.IPApiProxy = api.proxy
		quality.IPApiHosting = api.hosting
		quality.IPApiMobile = api.mobile
		quality.ASN = api.as
		quality.IPApiISP = api.isp
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
		http.Header{"User-Agent": {browserUserAgent}}, nil)
	if err != nil {
		return -1
	}
	defer response.Body.Close()
	io.Copy(io.Discard, io.LimitReader(response.Body, 4096))

	return time.Since(start).Milliseconds()
}
