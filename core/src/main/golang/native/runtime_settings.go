package main

//#include "bridge.h"
import "C"

import (
	"cfa/native/config"
	"github.com/metacubex/mihomo/component/trafficpolicy"
)

//export setRuntimeSettings
func setRuntimeSettings(value C.c_string) { config.SetRuntimeSettings(C.GoString(value)) }

//export setTrafficPolicy
func setTrafficPolicy(policy C.int) { trafficpolicy.Set(trafficpolicy.Policy(policy)) }
