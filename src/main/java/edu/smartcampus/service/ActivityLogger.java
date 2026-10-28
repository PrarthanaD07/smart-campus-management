package edu.smartcampus.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public final class ActivityLogger {
    private final Path logFile = Path.of("data", "activity.log");

    public synchronized void log(String message) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, LocalDateTime.now() + " | " + message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write activity log: " + exception.getMessage(), exception);
        }
    }
}
