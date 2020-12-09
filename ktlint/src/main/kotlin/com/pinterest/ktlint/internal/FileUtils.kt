package com.pinterest.ktlint.internal

import com.pinterest.ktlint.core.KtLint
import com.pinterest.ktlint.core.LintError
import com.pinterest.ktlint.core.RuleSet
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.system.exitProcess

internal val workDir: String = File(".").canonicalPath

internal fun List<String>.fileSequence(): Sequence<File> {
    val patterns = if (isEmpty()) {
       listOf("**/*.kt", "**/*.kts")
    } else {
        map(::expandTilde).toList()
    }

    return FileMatcher(File(workDir), patterns).getFiles()
}

/**
 * List of paths to Java `jar` files.
 */
internal typealias JarFiles = List<String>

internal fun JarFiles.toFilesURIList() = map {
    val jarFile = File(expandTilde(it))
    if (!jarFile.exists()) {
        println("Error: $it does not exist")
        exitProcess(1)
    }
    jarFile.toURI().toURL()
}

// a complete solution would be to implement https://www.gnu.org/software/bash/manual/html_node/Tilde-Expansion.html
// this implementation takes care only of the most commonly used case (~/)
private fun expandTilde(path: String): String = path.replaceFirst(Regex("^(!)?~"), "$1" + System.getProperty("user.home"))

internal fun File.location(
    relative: Boolean
) = if (relative) this.toRelativeString(File(workDir)) else this.path

/**
 * Run lint over common kotlin file or kotlin script file.
 */
internal fun lintFile(
    fileName: String,
    fileContents: String,
    ruleSets: List<RuleSet>,
    userData: Map<String, String> = emptyMap(),
    editorConfigPath: String? = null,
    debug: Boolean = false,
    lintErrorCallback: (LintError) -> Unit = {}
) {
    KtLint.lint(
        KtLint.Params(
            fileName = fileName,
            text = fileContents,
            ruleSets = ruleSets,
            userData = userData,
            script = !fileName.endsWith(".kt", ignoreCase = true),
            editorConfigPath = editorConfigPath,
            cb = { e, _ ->
                lintErrorCallback(e)
            },
            debug = debug
        )
    )
}

/**
 * Format a kotlin file or script file
 */
internal fun formatFile(
    fileName: String,
    fileContents: String,
    ruleSets: Iterable<RuleSet>,
    userData: Map<String, String>,
    editorConfigPath: String?,
    debug: Boolean,
    cb: (e: LintError, corrected: Boolean) -> Unit
): String =
    KtLint.format(
        KtLint.Params(
            fileName = fileName,
            text = fileContents,
            ruleSets = ruleSets,
            userData = userData,
            script = !fileName.endsWith(".kt", ignoreCase = true),
            editorConfigPath = editorConfigPath,
            cb = cb,
            debug = debug
        )
    )


internal class FileMatcher(val baseDirectory: File, patterns: List<String>) {

    private val patterns = patterns.map {
        println("Pattern : ${!it.startsWith("!")} : ${it.removePrefix("!")}")
        Triple<Boolean, PathMatcher, String>(!it.startsWith("!"), FileSystems.getDefault().getPathMatcher("glob:${it.removePrefix("!")}"), it)
    }

    fun matches(path: Path): Boolean {
        var isIncluded = false

        for (pattern in patterns) {
            if (isIncluded) {
                if (!pattern.first) {
                    isIncluded = !pattern.second.matches(path).also {
                        println("$path : ${pattern.third} ${if (it) "excludes this file" else ""}")
                    }
                }
            } else {
                if (pattern.first) {
                    isIncluded = pattern.second.matches(path).also {
                        println("$path : ${pattern.third} ${if (it) "includes this file" else ""}")
                    }
                }
            }
        }

        println("Checking : $isIncluded : $path")

        return isIncluded
    }

    fun getFiles() = baseDirectory.walkTopDown().filter { file ->
        if (file.isFile) {
            matches(baseDirectory.toPath().relativize(file.toPath()))
        } else {
            false
        }
    }
}
