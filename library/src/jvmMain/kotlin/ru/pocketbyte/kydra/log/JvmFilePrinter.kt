package ru.pocketbyte.kydra.log

import java.io.BufferedWriter
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JvmFilePrinter(
    private val maxSizeBytes: Long,
    private val maxFolderSizeBytes: Long,
    private val logDirectory: Path,
    private val fsOperator: FileSystemOperator = NioFileSystemOperator(),
    private val dateProvider: () -> Date = { Date() },
) : Printer {
    private val lock = ReentrantLock()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    private val timeFormat = SimpleDateFormat("HH-mm-ss")
    private var currentLogDate: String = getCurrentDate()
    private var currentLogPath: Path = getCurrentLogPath()
    private var writer: BufferedWriter? = null

    private fun getCurrentDate(): String = dateFormat.format(dateProvider())

    private fun getCurrentTime(): String = timeFormat.format(dateProvider())

    private fun getCurrentLogPath(): Path = logDirectory.resolve(
        "$LOG_FILE_NAME_PREFIX-${currentLogDate}_${getCurrentTime()}.log"
    )

    init {
        if (fsOperator.exists(logDirectory).not()) {
            fsOperator.createDirectories(logDirectory)
        }
        Runtime.getRuntime().addShutdownHook(Thread { writer?.close() })
    }

    override fun print(message: String) {
        lock.withLock {
            try {
                rotateFileIfNeeded()
                if (writer == null) {
                    writer = createWriter()
                    checkAndCleanDirectory()
                }
                writer?.apply {
                    write("${message}\n")
                    flush()
                }
            } catch (e: Exception) {
                System.err.println("File print log error: ${e.message}")
                writer?.close()
                writer = null
            }
        }
    }

    private fun createWriter(): BufferedWriter {
        if (fsOperator.exists(currentLogPath).not()) {
            fsOperator.createFile(currentLogPath)
        }
        return fsOperator.newBufferedWriter(
            currentLogPath,
            setOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        )
    }

    private fun rotateFileIfNeeded() {
        val today = getCurrentDate()
        if (today != currentLogDate) {
            currentLogDate = today
            rotateFile()
        }
        if (fsOperator.exists(currentLogPath) && fsOperator.size(currentLogPath) >= maxSizeBytes) {
            rotateFile()
        }
    }

    private fun rotateFile() {
        writer?.close()
        writer = null
        currentLogPath = getCurrentLogPath()
    }

    private fun checkAndCleanDirectory() {
        val logFiles = getSortedLogFiles()
        var totalSize = logFiles.sumOf { fsOperator.size(it) }
        val maxFolderSize = maxFolderSizeBytes - maxSizeBytes

        while (totalSize >= maxFolderSize && logFiles.size > 1) {
            val oldest = logFiles.first()
            totalSize -= fsOperator.size(oldest)
            fsOperator.deleteIfExists(oldest)
            logFiles.removeFirst()
        }
    }

    private fun getSortedLogFiles(): MutableList<Path> {
        return fsOperator.listFiles(logDirectory)
            .sortedBy { fsOperator.getLastModifiedTime(it) }
            .toMutableList()
    }

    companion object {
        private const val LOG_FILE_NAME_PREFIX = "logs"
    }
}
