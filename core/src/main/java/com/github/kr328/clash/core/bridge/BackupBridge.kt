package com.github.kr328.clash.core.bridge

/** Backup-only JNI: no runtime reset, no global subscription key mutation. */
object BackupBridge {
    init { System.loadLibrary("bridge") }
    external fun cryptFile(input: String, output: String, password: ByteArray, decrypt: Boolean): String?
    external fun providerInventory(configPath: String, secretKeys: String, override: String): String
    external fun beginSnapshot()
    external fun endSnapshot()
}
