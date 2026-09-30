package com.acme.salary;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.file.Path;
import java.util.UUID;

public final class TemporarySqliteDatabase {

    private TemporarySqliteDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        Path databaseFile = Path.of(System.getProperty("java.io.tmpdir"), "salary-kata-test-" + UUID.randomUUID() + ".db");
        databaseFile.toFile().deleteOnExit();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseFile + "?foreign_keys=true");
    }
}
