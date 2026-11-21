package com.documentplatform.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The library is shared by LexaVault and SDV, so it must not name either product. The build fails if any source,
 * test or POM in the library mentions a product package. The names are assembled here so this file passes itself.
 */
class NoProductNamesGuardTest {

    private static final List<String> FORBIDDEN = List.of("com." + "securedoc", "com." + "sdv.");

    @Test
    void librarySourcesNameNoProduct() throws IOException {
        Path root = Path.of("").toAbsolutePath().getParent();
        assertTrue(Files.exists(root.resolve("pom.xml")), "expected to run from a module of the library: " + root);
        try (Stream<Path> files = Files.walk(root)) {
            List<String> offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("target") && !path.toString().contains(".git"))
                    .filter(path -> path.toString().endsWith(".java") || path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> FORBIDDEN.stream().anyMatch(name -> read(path).contains(name)))
                    .map(path -> root.relativize(path).toString())
                    .toList();
            if (!offenders.isEmpty()) fail("Product names found in the shared library: " + offenders);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
