package tunnel

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	clashHttp "github.com/metacubex/mihomo/component/http"
)

type rawIPInfo struct {
	IP              string `json:"ip"`
	Country         string `json:"country"`
	CountryCode     string `json:"country_code"`
	Region          string `json:"region"`
	City            string `json:"city"`
	ASN             int    `json:"asn"`
	ASNOrganization string `json:"asn_organization"`
	Organization    string `json:"organization"`
}

type ipInfo struct {
	IP          string `json:"ip"`
	Country     string `json:"country"`
	CountryCode string `json:"country_code"`
	City        string `json:"city"`
	ISP         string `json:"isp"`
	ASN         string `json:"asn"`
}

const (
	ipInfoURL     = "https://api.ip.sb/geoip"
	ipInfoTimeout = 10 * time.Second
	ipInfoMaxBody = 1 << 20
)

// QueryIPInfo queries the current external IP information.
// An empty proxy routes the request through the tunnel (rule mode);
// otherwise the request is sent via the given special proxy (e.g. "DIRECT").
func QueryIPInfo(proxy string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), ipInfoTimeout)
	defer cancel()

	var opts []clashHttp.Option
	if proxy != "" {
		opts = append(opts, clashHttp.WithSpecialProxy(proxy))
	}

	response, err := clashHttp.HttpRequest(ctx, ipInfoURL, http.MethodGet,
		http.Header{"User-Agent": {"clash.meta"}}, nil, opts...)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return "", fmt.Errorf("unexpected status %d from ip api", response.StatusCode)
	}

	body, err := io.ReadAll(io.LimitReader(response.Body, ipInfoMaxBody))
	if err != nil {
		return "", err
	}

	var raw rawIPInfo
	if err := json.Unmarshal(body, &raw); err != nil {
		return "", err
	}

	isp := raw.Organization
	if isp == "" {
		isp = raw.ASNOrganization
	}

	asn := ""
	if raw.ASN != 0 {
		if raw.ASNOrganization != "" {
			asn = fmt.Sprintf("AS%d %s", raw.ASN, raw.ASNOrganization)
		} else {
			asn = fmt.Sprintf("AS%d", raw.ASN)
		}
	}

	data, err := json.Marshal(ipInfo{
		IP:          raw.IP,
		Country:     raw.Country,
		CountryCode: raw.CountryCode,
		City:        raw.City,
		ISP:         isp,
		ASN:         asn,
	})
	if err != nil {
		return "", err
	}

	return string(data), nil
}
