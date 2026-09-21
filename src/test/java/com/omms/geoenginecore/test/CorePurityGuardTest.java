package com.omms.geoenginecore.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dependency-rule conformance (TECHSPEC §7; Phase 1 acceptance
 * criterion "core compiles without Minecraft").
 *
 * <p>Walks the pure core source tree and fails if any file imports a
 * Minecraft/NeoForge platform type. The core must stay compilable and
 * testable in a plain JDK environment.
 */
public class CorePurityGuardTest {

    @Test
    @DisplayName("No Minecraft/NeoForge platform imports anywhere in the pure core")
    void testNoPlatformImportsInCore() throws IOException {
        Path root = Path.of("src/main/java/com/omms/geoenginecore");
        assertTrue(Files.isDirectory(root), "test must run from the repository root");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    for (String line : Files.readAllLines(path)) {
                        if (line.startsWith("import net.minecraft")
                                || line.startsWith("import net.neoforged")
                                || line.startsWith("import com.mojang")) {
                            violations.add(path + ": " + line.trim());
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        assertTrue(violations.isEmpty(), "platform imports found in the pure core: " + violations);
    }
}
