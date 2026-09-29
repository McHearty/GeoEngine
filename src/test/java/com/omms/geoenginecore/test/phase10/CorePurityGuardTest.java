package com.omms.geoenginecore.test.phase10;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dependency-rule conformance (TECHSPEC §7; Phase 1 acceptance
 * criterion "core compiles without Minecraft").
 *
 * <p>Walks the pure core source tree and fails if any file imports a
 * Minecraft/NeoForge platform type, or references a method that only
 * exists because a mod toolchain patches {@code java.lang} (e.g.
 * {@code Math.clamp} — absent from a standard JDK). The core must
 * stay compilable and testable in a plain JDK environment.
 */
public class CorePurityGuardTest {

    /**
     * Matches a toolchain-provided {@code Math.clamp} reference in
     * code. The lookbehind excludes identifier continuations so the
     * core-local {@code GeoMath.clamp} (a pure-JDK replacement) does
     * not match: in {@code GeoMath.clamp(...)} the character before
     * {@code Math} is an identifier letter, in {@code Math.clamp(...)}
     * it is not. The scan feeds this pattern comment-free code only
     * (see the line handling in the walk), so documentation mentions
     * of {@code Math.clamp} do not trip the guard — the authoritative
     * plain-JDK compilability check remains the separate plain-JDK
     * compile of the core tree.
     */
    private static final Pattern TOOLCHAIN_MATH_CLAMP =
        Pattern.compile("(?<![\\p{L}_$])Math\\.clamp");

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
                        String t = line.trim();
                        if (t.startsWith("import net.minecraft")
                                || t.startsWith("import net.neoforged")
                                || t.startsWith("import com.mojang")) {
                            violations.add(path + ": " + t);
                        }
                        if (t.startsWith("*") || t.startsWith("/*") || t.startsWith("//")) {
                            continue; // documentation, not code
                        }
                        // Toolchains patch java.lang with Math.clamp; a
                        // standard JDK has no such method, so the core
                        // must use the core-local GeoMath instead. Strip
                        // trailing line comments before matching so a
                        // documented mention does not trip the guard.
                        String code = line.split("//", 2)[0];
                        if (TOOLCHAIN_MATH_CLAMP.matcher(code).find()) {
                            violations.add(path + ": " + t);
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
