package com.github.kr328.clash.log

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.util.logsDir
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

class LogcatWriter(context: Context) : AutoCloseable {
    private val logsDir = context.applicationContext.logsDir

    private var file = LogFile.generate()
    private var writer = openWriter(file)
    private var writtenBytes = 0L
    private var pendingMessages = 0
    private var lastFlushTime = System.currentTimeMillis()

    init {
        pruneOldLogs()
    }

    override fun close() {
        runCatching {
            writer.flush()
            writer.close()
        }.onFailure {
            Log.w("Close log file ${file.fileName}: $it", it)
        }
    }

    fun appendMessage(message: LogMessage) {
        val line = FORMAT.format(message.time.time, message.level.name, message.message)

        writer.appendLine(line)

        writtenBytes += line.toByteArray(Charsets.UTF_8).size + 1
        pendingMessages += 1

        val now = System.currentTimeMillis()

        if (now - lastFlushTime >= FLUSH_INTERVAL_MS || pendingMessages >= FLUSH_MESSAGE_COUNT) {
            writer.flush()

            lastFlushTime = now
            pendingMessages = 0
        }

        if (writtenBytes >= MAX_FILE_BYTES) {
            rotate()
        }
    }

    private fun rotate() {
        runCatching {
            writer.flush()
            writer.close()

            val current = logsDir.resolve(file.fileName)
            val rotated = File(current.path + ROTATED_SUFFIX)

            if (rotated.exists() && !rotated.delete()) {
                throw IOException("Fail to delete old rotated log ${rotated.name}")
            }

            if (!current.renameTo(rotated)) {
                throw IOException("Fail to rename ${current.name} to ${rotated.name}")
            }

            val next = LogFile.generate()

            writer = openWriter(next)
            file = next
            writtenBytes = 0L
            pendingMessages = 0
            lastFlushTime = System.currentTimeMillis()
        }.onFailure {
            Log.w("Rotate log file ${file.fileName}: $it", it)

            // Keep logging into a file with the current name, even if the
            // rotation only partially completed.
            runCatching {
                writer = openWriter(file)

                writtenBytes = 0L
                pendingMessages = 0
                lastFlushTime = System.currentTimeMillis()
            }.onFailure { e ->
                Log.w("Reopen log file ${file.fileName}: $e", e)
            }
        }

        pruneOldLogs()
    }

    private fun openWriter(logFile: LogFile): BufferedWriter {
        logsDir.mkdirs()

        return BufferedWriter(FileWriter(logsDir.resolve(logFile.fileName), true))
    }

    private fun pruneOldLogs() {
        runCatching {
            val files = logsDir.listFiles { child ->
                child.isFile && child.name.startsWith(LOG_FILE_PREFIX) &&
                        (child.name.endsWith(LOG_FILE_SUFFIX) || child.name.endsWith(ROTATED_SUFFIX))
            } ?: return@runCatching

            files.sortedByDescending { it.lastModified() }
                .drop(MAX_LOG_FILES)
                .forEach { stale ->
                    if (!stale.delete()) {
                        Log.w("Fail to delete old log file ${stale.name}")
                    }
                }
        }.onFailure {
            Log.w("Prune old logs: $it", it)
        }
    }

    companion object {
        private const val FORMAT = "%d:%s:%s"

        private const val MAX_FILE_BYTES = 8L * 1024 * 1024
        private const val MAX_LOG_FILES = 8
        private const val FLUSH_INTERVAL_MS = 2000L
        private const val FLUSH_MESSAGE_COUNT = 128

        private const val LOG_FILE_PREFIX = "clash-"
        private const val LOG_FILE_SUFFIX = ".log"
        private const val ROTATED_SUFFIX = ".log.1"
    }
}
