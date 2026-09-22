package com.github.zeroqli.ticketmasta.services

import java.nio.file.Path

/**
 * Normalizes a project base path for local file access.
 *
 * `project.basePath` may arrive as a WSL UNC path (`//wsl.localhost/...` or `\\wsl.localhost\...`),
 * which [Path.of] rejects on Windows, so its separators are converted to backslashes. Plain Windows
 * paths are already valid, and Unix paths must be left untouched.
 */
fun normalizeBasePath(basePath: String): Path =
    if (basePath.startsWith("//") || basePath.startsWith("\\\\")) {
        Path.of(basePath.replace('/', '\\'))
    } else {
        Path.of(basePath)
    }