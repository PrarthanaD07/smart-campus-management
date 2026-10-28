package edu.smartcampus.service;

import edu.smartcampus.model.Student;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class CsvFileService {
    public Path exportStudents(List<Student> students, Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write("id,name,email,program,year");
                writer.newLine();
                for (Student student : students) {
                    writer.write(String.join(",", csv(student.getId()), csv(student.getName()),
                            csv(student.getEmail()), csv(student.getProgram()), Integer.toString(student.getYear())));
                    writer.newLine();
                }
            }
            return file;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not export students to " + file + ": " + exception.getMessage(),
                    exception);
        }
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
