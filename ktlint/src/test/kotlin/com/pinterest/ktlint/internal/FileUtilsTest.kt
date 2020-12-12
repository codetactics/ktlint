package com.pinterest.ktlint.internal

import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsTest {

    @Test
    fun negation() {
        val filter = FileMatcher(listOf(
            "**.kt",
            "!**/*test*/**.kt",
            "!**/prefix*/**.kt",
            "!**/*suffix/**.kt"
        ))

        assertTrue(filter.matches(Paths.get("a.kt")))
        assertFalse(filter.matches(Paths.get("a/test_/a.kt")))
        assertFalse(filter.matches(Paths.get("a/_test_/a.kt")))
        assertFalse(filter.matches(Paths.get("a/_test/a.kt")))
        assertFalse(filter.matches(Paths.get("a/prefix_/a.kt")))
        assertFalse(filter.matches(Paths.get("a/prefix/a.kt")))
        assertTrue(filter.matches(Paths.get("a/_prefix/a.kt")))
        assertFalse(filter.matches(Paths.get("a/_suffix/a.kt")))
        assertFalse(filter.matches(Paths.get("a/suffix/a.kt")))
        assertTrue(filter.matches(Paths.get("a/suffix_/a.kt")))
    }

    @Test
    fun overlappingFilePatternTest() {
        val result = listOf("src/test/**/*.kt", "!src/test/resources/**").fileSequence()

        result.forEach {
            assertTrue("File should have been excluded : ${it.path}", !it.path.contains("/resources/"))
        }
    }

}
