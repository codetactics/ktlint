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

/**
 * For a list of Strings that represent file matching patterns,
 * returns a [Sequence] of Files that match the patterns.
 */
internal fun List<String>.fileSequence(): Sequence<File> {
    val patterns = if (isEmpty()) {
       listOf("**/*.kt", "**/*.kts")
    } else {
        map(::expandTilde).toList()
    }

    return FileMatcher(patterns).getFiles(File(workDir))
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

/**
 * Given a list of Patterns, provides Path matching
 * and convenient searching for files that match.
 */
internal class FileMatcher(patterns: List<String>) {

    /**
     * A list of [Triple] values, each representing an individual pattern
     * and the important details of it.
     * Each triple contains:
     * - isNegated : Whether the pattern is used to exclude files from matching.
     * - PathMatcher : The [PathMatcher] resulting from compiling the pattern.
     * - The original pattern string, for debugging / logging purposes.
     */
    private val patterns = patterns.map { pattern ->
        val patternType = if (pattern.startsWith("regex:")) {
            "regex"
        } else {
            "glob"
        }

        // Remove any pattern type prefix and determine if the pattern is negated.
        val isNegated:Boolean
        val patternOnly = pattern.removePrefix("$patternType:").let {
            isNegated = !it.startsWith("!")
            it.removePrefix("!")
        }

        // Create a Triple of isNegated, the resulting PathMatcher, and the original pattern.
        Triple<Boolean, PathMatcher, String>(isNegated, FileSystems.getDefault().getPathMatcher("$patternType:$patternOnly"), pattern)
    }

    /**
     * Determine if the given [path] matches the list of file patterns.
     * Be sure the path cardinality (relative or absolute), matches the
     * cardinality of the patterns you are attempting to match.
     */
    fun matches(path: Path): Boolean {
        var isIncluded = false

        for (pattern in patterns) {
            if (isIncluded) {
                if (!pattern.first) {
                    isIncluded = !pattern.second.matches(path)
                }
            } else {
                if (pattern.first) {
                    isIncluded = pattern.second.matches(path)
                }
            }
        }

        return isIncluded
    }

    /**
     * Get all files that match the given patterns in a directory tree.
     */
    fun getFiles(baseDirectory: File) = baseDirectory.walkTopDown().filter { file ->
        if (file.isFile) {
            matches(baseDirectory.toPath().relativize(file.toPath()))
        } else {
            false
        }
    }
}
