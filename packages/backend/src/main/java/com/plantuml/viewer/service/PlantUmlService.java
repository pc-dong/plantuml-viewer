package com.plantuml.viewer.service;

import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.FileFormat;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PlantUmlService {

    public String generateSvg(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Source cannot be empty");
        }
        try {
            SourceStringReader reader = new SourceStringReader(source);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            reader.outputImage(os, new FileFormatOption(FileFormat.SVG));
            return os.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate SVG", e);
        }
    }

    public String detectDiagramType(String source) {
        String lower = source.toLowerCase();
        if (lower.contains("usecase") || lower.contains("use case")) {
            return "usecase";
        }
        if (lower.contains("actor") && (lower.contains("->") || lower.contains("-->"))) {
            return "sequence";
        }
        if (lower.contains("component") && !lower.contains("class")) {
            return "component";
        }
        if (lower.contains("start") && lower.contains("stop")) {
            return "activity";
        }
        return "class";
    }
}
