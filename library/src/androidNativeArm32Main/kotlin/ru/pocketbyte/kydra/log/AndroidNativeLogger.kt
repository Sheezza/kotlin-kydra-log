/*
 * Copyright © 2022 Denis Shurygin. All rights reserved.
 * Licensed under the Apache License, Version 2.0
 */

package ru.pocketbyte.kydra.log

import platform.android.*

/**
 * Android Native implementation of Logger that writes logs using __android_log_print.
 */
open class AndroidNativeLogger: AbsLogger() {

    override fun doLog(level: LogLevel, tag: String?, string: String) {
        __android_log_print(level.native.toInt(), tag, string)
    }

    override fun doLog(level: LogLevel, tag: String?, exception: Throwable) {
        if (exception.message != null) {
            log(level, tag, "${qualifiedName(exception)}: " +
                    "${exception.message ?: ""}\n${stackTrace(exception)}")
        } else {
            log(level, tag, "${qualifiedName(exception)}\n${stackTrace(exception)}")
        }
    }

    open fun stackTrace(exception: Throwable): String {
        return exception.getStackTrace().joinToString("\n")
    }

    open fun qualifiedName(exception: Throwable): String {
        return exception::class.qualifiedName!!
    }
}
