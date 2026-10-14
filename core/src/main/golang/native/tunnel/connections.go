package tunnel

import (
	"encoding/json"

	"github.com/metacubex/mihomo/tunnel/statistic"
)

// ConnectionItem describes a single active connection.
type ConnectionItem struct {
	ID          string `json:"id"`
	Network     string `json:"network"`
	Type        string `json:"type"`
	Host        string `json:"host"`
	Destination string `json:"destination"`
	Source      string `json:"source"`
	Process     string `json:"process"`
	Rule        string `json:"rule"`
	Chains      string `json:"chains"`
	Upload      int64  `json:"upload"`
	Download    int64  `json:"download"`
	Start       int64  `json:"start"`
}

// QueryConnections returns a JSON array of active connections.
func QueryConnections() (string, error) {
	snapshot := statistic.DefaultManager.Snapshot()

	items := make([]ConnectionItem, 0, len(snapshot.Connections))

	for _, c := range snapshot.Connections {
		if c == nil || c.Metadata == nil {
			continue
		}

		item := ConnectionItem{
			ID:          c.UUID.String(),
			Network:     c.Metadata.NetWork.String(),
			Type:        c.Metadata.Type.String(),
			Host:        c.Metadata.Host,
			Destination: c.Metadata.RemoteAddress(),
			Source:      c.Metadata.SourceAddress(),
			Process:     c.Metadata.Process,
			Chains:      c.Chain.String(),
			Upload:      c.UploadTotal.Load(),
			Download:    c.DownloadTotal.Load(),
			Start:       c.Start.Unix(),
		}

		if item.Host == "" {
			item.Host = c.Metadata.String()
		}

		if c.Rule != "" {
			if c.RulePayload != "" {
				item.Rule = c.Rule + "(" + c.RulePayload + ")"
			} else {
				item.Rule = c.Rule
			}
		}

		items = append(items, item)
	}

	data, err := json.Marshal(items)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

// CloseConnection terminates the connection with the given ID.
func CloseConnection(id string) {
	if tracker := statistic.DefaultManager.Get(id); tracker != nil {
		_ = tracker.Close()
	}
}
