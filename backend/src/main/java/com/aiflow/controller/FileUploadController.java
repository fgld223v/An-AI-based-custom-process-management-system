package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文件上传/下载控制器。
 * 存储到本地文件系统，返回访问 URL。
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final Path uploadDir;

    /** 上传目录，默认 ./uploads */
    public FileUploadController(@Value("${app.upload.dir:./uploads}") String uploadDirPath) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadDirPath, e);
        }
    }

    /**
     * 单文件上传。
     * @return { "fileName", "originalName", "url", "size" }
     */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("文件为空");

        try {
            // 生成唯一文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String safeName = sanitize(file.getOriginalFilename());
            String storedName = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;

            Path targetPath = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String url = "/api/files/download/" + storedName;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", storedName);
            result.put("originalName", file.getOriginalFilename());
            result.put("url", url);
            result.put("size", file.getSize());

            log.info("文件上传成功: {} ({} bytes)", storedName, file.getSize());
            return ApiResponse.success(result);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 多文件上传。
     */
    @PostMapping("/upload/batch")
    public ApiResponse<List<Map<String, Object>>> uploadBatch(@RequestParam("files") MultipartFile[] files) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            // 模拟单文件上传逻辑
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                String safeName = sanitize(file.getOriginalFilename());
                String storedName = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
                Path targetPath = uploadDir.resolve(storedName);
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("fileName", storedName);
                result.put("originalName", file.getOriginalFilename());
                result.put("url", "/api/files/download/" + storedName);
                result.put("size", file.getSize());
                results.add(result);
            } catch (IOException e) {
                log.error("批量上传失败: {}", file.getOriginalFilename(), e);
            }
        }
        return ApiResponse.success(results);
    }

    /**
     * 文件下载/访问。
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        try {
            Path filePath = uploadDir.resolve(sanitize(fileName)).normalize();
            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.status(403).build();
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(filePath);
            return ResponseEntity.ok()
                    .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 定时清理超过 7 天的孤立上传文件（每天凌晨 3 点执行）。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOrphanFiles() {
        try {
            long cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
            java.io.File[] files = uploadDir.toFile().listFiles();
            if (files == null) return;
            int cleaned = 0;
            for (java.io.File file : files) {
                if (file.isFile() && file.lastModified() < cutoff) {
                    if (file.delete()) cleaned++;
                }
            }
            if (cleaned > 0) log.info("文件清理完成，删除 {} 个超过 7 天的文件", cleaned);
        } catch (Exception e) {
            log.warn("文件清理异常: {}", e.getMessage());
        }
    }

    private String sanitize(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9.\\-_\\u4e00-\\u9fa5]", "_");
    }
}
