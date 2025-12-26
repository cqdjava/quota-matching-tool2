package com.enterprise.quota.service;

import com.enterprise.quota.util.WordDocumentProcessor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private final WordDocumentProcessor processor;
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "quota-docs";

    public DocumentService() {
        this.processor = new WordDocumentProcessor();
        // Create temp directory if it doesn't exist
        File tempDir = new File(TEMP_DIR);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
    }

    /**
     * Process document with replacements
     * @param templateFile Template file
     * @param replacements Replacement map
     * @return Path to generated document
     * @throws Exception Processing exception
     */
    public String processDocument(MultipartFile templateFile, Map<String, String> replacements) throws Exception {
        // Save uploaded template to temp directory
        String templateFileName = UUID.randomUUID().toString() + "_" + templateFile.getOriginalFilename();
        Path templatePath = Paths.get(TEMP_DIR, templateFileName);
        Files.createDirectories(templatePath.getParent());
        
        try (FileOutputStream fos = new FileOutputStream(templatePath.toFile())) {
            fos.write(templateFile.getBytes());
        }

        // Generate output file path
        String outputFileName = "generated_" + UUID.randomUUID().toString() + ".docx";
        Path outputPath = Paths.get(TEMP_DIR, outputFileName);

        // Process document
        processor.processDocument(templatePath.toString(), outputPath.toString(), replacements);

        // Clean up template file
        Files.deleteIfExists(templatePath);

        return outputPath.toString();
    }
    
    /**
     * Process document with replacements from File
     * @param templateFile Template file
     * @param replacements Replacement map
     * @return Path to generated document
     * @throws Exception Processing exception
     */
    public String processDocument(File templateFile, Map<String, String> replacements) throws Exception {
        // Generate output file path
        String outputFileName = "generated_" + UUID.randomUUID().toString() + ".docx";
        Path outputPath = Paths.get(TEMP_DIR, outputFileName);

        // Process document
        processor.processDocument(templateFile.getAbsolutePath(), outputPath.toString(), replacements);

        return outputPath.toString();
    }

    /**
     * Parse replacement rules from text
     * Format: ${key}=value (one per line)
     */
    public Map<String, String> parseReplacementRules(String text) {
        Map<String, String> replacements = new HashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return replacements;
        }

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Check if line contains =
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    // Validate key format (should be ${key})
                    if (key.startsWith("${") && key.endsWith("}")) {
                        if (!value.isEmpty()) {
                            replacements.put(key, value);
                        }
                    }
                }
            }
        }

        return replacements;
    }

    /**
     * Get file bytes for download
     */
    public byte[] getFileBytes(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllBytes(path);
    }

    /**
     * Delete temporary file
     */
    public void deleteTempFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            // Ignore deletion errors
        }
    }
}

