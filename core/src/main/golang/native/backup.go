package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"cfa/native/backup"
	"encoding/json"
	"github.com/metacubex/mihomo/component/providerwrite"
	"unsafe"
)

//export backupCryptFile
func backupCryptFile(input, output *C.char, password unsafe.Pointer, passwordLen C.int, decrypt C.int) *C.char {
	secret := C.GoBytes(password, passwordLen)
	defer func() {
		for i := range secret {
			secret[i] = 0
		}
	}()
	if err := backup.CryptFile(C.GoString(input), C.GoString(output), secret, decrypt != 0); err != nil {
		return C.CString(err.Error())
	}
	return nil
}

//export backupProviderInventory
func backupProviderInventory(configPath, secretKeys, override *C.char) *C.char {
	inventory, err := backup.Inventory(C.GoString(configPath), C.GoString(secretKeys), C.GoString(override))
	if err != nil {
		data, _ := json.Marshal(map[string]string{"error": err.Error()})
		return C.CString(string(data))
	}
	data, _ := json.Marshal(inventory)
	return C.CString(string(data))
}

//export backupBeginSnapshot
func backupBeginSnapshot() { providerwrite.BeginSnapshot() }

//export backupEndSnapshot
func backupEndSnapshot() { providerwrite.EndSnapshot() }
