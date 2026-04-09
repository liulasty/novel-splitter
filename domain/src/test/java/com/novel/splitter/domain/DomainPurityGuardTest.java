package com.novel.splitter.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainPurityGuardTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import org.springframework.",
            "import jakarta.persistence.",
            "import jakarta.transaction."
    );

    @Test
    void domainMustNotDependOnFrameworkImports() throws IOException {
        Path srcRoot = Path.of("src/main/java/com/novel/splitter/domain");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(srcRoot)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> checkFile(path, violations));
        }

        assertTrue(violations.isEmpty(), () ->
                "Domain framework leak detected:\n" + String.join("\n", violations));
    }

    private void checkFile(Path path, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }

        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            for (String forbiddenPrefix : FORBIDDEN_IMPORT_PREFIXES) {
                if (trimmed.startsWith(forbiddenPrefix)) {
                    violations.add(path + ":" + (i + 1) + " -> " + trimmed);
                }
            }
        }
    }
}
