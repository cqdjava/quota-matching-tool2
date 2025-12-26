package com.enterprise.quota.controller;

import com.enterprise.quota.entity.DocumentTemplate;
import com.enterprise.quota.entity.ReplacementTemplate;
import com.enterprise.quota.service.DocumentService;
import com.enterprise.quota.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private TemplateService templateService;

    /**
     * Generate document from template with replacements
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateDocument(
            @RequestParam("template") MultipartFile templateFile,
            @RequestParam("replacements") String replacementsText) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Validate template file
            if (templateFile == null || templateFile.isEmpty()) {
                result.put("success", false);
                result.put("message", "请选择模板文件");
                return ResponseEntity.badRequest().body(result);
            }

            // Parse replacement rules
            Map<String, String> replacements = documentService.parseReplacementRules(replacementsText);
            if (replacements.isEmpty()) {
                result.put("success", false);
                result.put("message", "请至少输入一个替换规则（格式：${占位符}=替换内容）");
                return ResponseEntity.badRequest().body(result);
            }

            // Process document
            String outputPath = documentService.processDocument(templateFile, replacements);

            // Read file bytes
            byte[] fileBytes = documentService.getFileBytes(outputPath);

            // Clean up temp file
            documentService.deleteTempFile(outputPath);

            // Return file for download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String fileName = "generated_" + System.currentTimeMillis() + ".docx";
            // 使用标准格式设置Content-Disposition头
            headers.setContentDispositionFormData("attachment", fileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "文档生成失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 使用服务器模板生成文档
     */
    @PostMapping("/generate-from-template")
    public ResponseEntity<?> generateDocumentFromTemplate(
            @RequestParam("templateId") Long templateId,
            @RequestParam("replacements") String replacementsText) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            DocumentTemplate template = templateService.getDocumentTemplateById(templateId);
            if (template == null) {
                result.put("success", false);
                result.put("message", "模板不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            File templateFile = templateService.getDocumentTemplateFile(templateId);
            if (templateFile == null || !templateFile.exists()) {
                result.put("success", false);
                result.put("message", "模板文件不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            // 解析替换规则
            Map<String, String> replacements = documentService.parseReplacementRules(replacementsText);
            if (replacements.isEmpty()) {
                result.put("success", false);
                result.put("message", "请至少输入一个替换规则（格式：${占位符}=替换内容）");
                return ResponseEntity.badRequest().body(result);
            }
            
            // 处理文档（直接使用File）
            String outputPath = documentService.processDocument(templateFile, replacements);
            byte[] fileBytes = documentService.getFileBytes(outputPath);
            documentService.deleteTempFile(outputPath);
            
            // 返回文件
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String fileName = "generated_" + System.currentTimeMillis() + ".docx";
            headers.setContentDispositionFormData("attachment", fileName);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);
                    
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "文档生成失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    // ==================== 替换内容模板管理 ====================
    
    /**
     * 保存替换内容模板
     */
    @PostMapping("/replacement-template/save")
    public ResponseEntity<Map<String, Object>> saveReplacementTemplate(
            @RequestParam("templateName") String templateName,
            @RequestParam("replacements") String replacements,
            @RequestParam(value = "description", required = false) String description,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            String createdBy = (String) session.getAttribute("username");
            if (createdBy == null) {
                createdBy = "system";
            }
            ReplacementTemplate template = templateService.saveReplacementTemplate(
                templateName, replacements, description, createdBy);
            result.put("success", true);
            result.put("message", "模板保存成功");
            result.put("data", template);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 获取所有替换内容模板
     */
    @GetMapping("/replacement-template/list")
    public ResponseEntity<List<ReplacementTemplate>> getAllReplacementTemplates() {
        List<ReplacementTemplate> templates = templateService.getAllReplacementTemplates();
        return ResponseEntity.ok(templates);
    }
    
    /**
     * 根据ID获取替换内容模板
     */
    @GetMapping("/replacement-template/{id}")
    public ResponseEntity<ReplacementTemplate> getReplacementTemplate(@PathVariable Long id) {
        ReplacementTemplate template = templateService.getReplacementTemplateById(id);
        if (template != null) {
            return ResponseEntity.ok(template);
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 删除替换内容模板
     */
    @DeleteMapping("/replacement-template/{id}")
    public ResponseEntity<Map<String, Object>> deleteReplacementTemplate(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            templateService.deleteReplacementTemplate(id);
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    // ==================== 文档模板管理 ====================
    
    /**
     * 上传文档模板
     */
    @PostMapping("/template/upload")
    public ResponseEntity<Map<String, Object>> uploadDocumentTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateName") String templateName,
            @RequestParam(value = "description", required = false) String description,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null || 
                (!originalFileName.endsWith(".docx") && !originalFileName.endsWith(".doc"))) {
                result.put("success", false);
                result.put("message", "只支持 .docx 和 .doc 格式的文件");
                return ResponseEntity.badRequest().body(result);
            }
            
            String createdBy = (String) session.getAttribute("username");
            if (createdBy == null) {
                createdBy = "system";
            }
            
            DocumentTemplate template = templateService.uploadDocumentTemplate(
                file, templateName, description, createdBy);
            result.put("success", true);
            result.put("message", "上传成功");
            result.put("data", template);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "上传失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 获取所有文档模板
     */
    @GetMapping("/template/list")
    public ResponseEntity<List<DocumentTemplate>> getAllDocumentTemplates() {
        List<DocumentTemplate> templates = templateService.getAllDocumentTemplates();
        return ResponseEntity.ok(templates);
    }
    
    /**
     * 根据ID获取文档模板
     */
    @GetMapping("/template/{id}")
    public ResponseEntity<DocumentTemplate> getDocumentTemplate(@PathVariable Long id) {
        DocumentTemplate template = templateService.getDocumentTemplateById(id);
        if (template != null) {
            return ResponseEntity.ok(template);
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 删除文档模板
     */
    @DeleteMapping("/template/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocumentTemplate(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            templateService.deleteDocumentTemplate(id);
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}

