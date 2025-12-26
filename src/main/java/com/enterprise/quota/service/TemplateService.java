package com.enterprise.quota.service;

import com.enterprise.quota.entity.DocumentTemplate;
import com.enterprise.quota.entity.ReplacementTemplate;
import com.enterprise.quota.repository.DocumentTemplateRepository;
import com.enterprise.quota.repository.ReplacementTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class TemplateService {
    
    @Autowired
    private ReplacementTemplateRepository replacementTemplateRepository;
    
    @Autowired
    private DocumentTemplateRepository documentTemplateRepository;
    
    @Value("${document.template.upload-dir:./templates}")
    private String uploadDir;
    
    /**
     * 保存替换内容模板
     */
    public ReplacementTemplate saveReplacementTemplate(String templateName, String replacements, String description, String createdBy) {
        ReplacementTemplate template = new ReplacementTemplate();
        template.setTemplateName(templateName);
        template.setReplacements(replacements);
        template.setDescription(description);
        template.setCreatedBy(createdBy);
        return replacementTemplateRepository.save(template);
    }
    
    /**
     * 获取所有替换内容模板
     */
    public List<ReplacementTemplate> getAllReplacementTemplates() {
        return replacementTemplateRepository.findAllByOrderByUpdatedAtDesc();
    }
    
    /**
     * 根据ID获取替换内容模板
     */
    public ReplacementTemplate getReplacementTemplateById(Long id) {
        return replacementTemplateRepository.findById(id).orElse(null);
    }
    
    /**
     * 删除替换内容模板
     */
    public void deleteReplacementTemplate(Long id) {
        replacementTemplateRepository.deleteById(id);
    }
    
    /**
     * 搜索替换内容模板
     */
    public List<ReplacementTemplate> searchReplacementTemplates(String keyword) {
        return replacementTemplateRepository.findByTemplateNameContainingIgnoreCase(keyword);
    }
    
    /**
     * 上传文档模板文件
     */
    public DocumentTemplate uploadDocumentTemplate(MultipartFile file, String templateName, String description, String createdBy) throws IOException {
        // 确保上传目录存在
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // 生成唯一文件名
        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(uniqueFileName);
        
        // 保存文件
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // 保存模板记录
        DocumentTemplate template = new DocumentTemplate();
        template.setTemplateName(templateName);
        template.setFileName(originalFileName);
        template.setFilePath(filePath.toString());
        template.setFileSize(file.getSize());
        template.setDescription(description);
        template.setCreatedBy(createdBy);
        
        return documentTemplateRepository.save(template);
    }
    
    /**
     * 获取所有文档模板
     */
    public List<DocumentTemplate> getAllDocumentTemplates() {
        return documentTemplateRepository.findAllByOrderByUpdatedAtDesc();
    }
    
    /**
     * 根据ID获取文档模板
     */
    public DocumentTemplate getDocumentTemplateById(Long id) {
        return documentTemplateRepository.findById(id).orElse(null);
    }
    
    /**
     * 删除文档模板
     */
    public void deleteDocumentTemplate(Long id) throws IOException {
        DocumentTemplate template = documentTemplateRepository.findById(id).orElse(null);
        if (template != null) {
            // 删除文件
            Path filePath = Paths.get(template.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
            // 删除记录
            documentTemplateRepository.deleteById(id);
        }
    }
    
    /**
     * 搜索文档模板
     */
    public List<DocumentTemplate> searchDocumentTemplates(String keyword) {
        return documentTemplateRepository.findByTemplateNameContainingIgnoreCase(keyword);
    }
    
    /**
     * 获取文档模板文件
     */
    public File getDocumentTemplateFile(Long id) {
        DocumentTemplate template = documentTemplateRepository.findById(id).orElse(null);
        if (template != null) {
            Path filePath = Paths.get(template.getFilePath());
            if (Files.exists(filePath)) {
                return filePath.toFile();
            }
        }
        return null;
    }
}

