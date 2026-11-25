package dev.miles.fliplink.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainPurityTest {

    @Test
    fun domain_layer_contains_zero_android_imports() {
        val candidates = listOf(
            File("src/main/kotlin/dev/miles/fliplink/domain"),
            File("app/src/main/kotlin/dev/miles/fliplink/domain"),
        )
        val domainDir = candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate domain directory from ${File(".").absolutePath}")

        val kotlinFiles = domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Expected domain Kotlin files", kotlinFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        for (file in kotlinFiles) {
            file.readLines().forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.")) {
                    violations.add("${file.path}:${idx + 1}: $trimmed")
                }
            }
        }
        assertTrue("Found forbidden Android imports in domain/: $violations", violations.isEmpty())
    }
}
