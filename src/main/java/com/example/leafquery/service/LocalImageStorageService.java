package com.example.leafquery.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalImageStorageService {

    private static final String DEFAULT_EXTENSION = ".jpg";
    private final Path uploadRoot;

    public LocalImageStorageService() {
        String projectDir = System.getProperty("user.dir");
        this.uploadRoot = Path.of(projectDir, "vue-frontend", "public", "images", "uploads");
    }

    public String saveIdentificationImage(MultipartFile file) {
        return saveIdentificationImageReference(file).getPublicUrl();
    }

    public StoredImageReference saveIdentificationImageReference(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }

        try {
            Path identificationDir = uploadRoot.resolve("identification");
            Files.createDirectories(identificationDir);

            String extension = resolveExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            Path targetPath = identificationDir.resolve(filename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredImageReference(
                    filename,
                    "/images/uploads/identification/" + filename,
                    targetPath,
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : filename
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to store identification image", e);
        }
    }

    public StoredImageReference resolveIdentificationImage(String imageToken) {
        if (imageToken == null || imageToken.isBlank()) {
            throw new IllegalArgumentException("Image token is blank");
        }

        String sanitized = sanitizeToken(imageToken);
        Path targetPath = uploadRoot.resolve("identification").resolve(sanitized).normalize();
        if (!targetPath.startsWith(uploadRoot.resolve("identification"))) {
            throw new IllegalArgumentException("Illegal image token path");
        }
        if (!Files.exists(targetPath)) {
            throw new IllegalArgumentException("Image token does not exist");
        }

        return new StoredImageReference(
                sanitized,
                "/images/uploads/identification/" + sanitized,
                targetPath,
                sanitized
        );
    }

    public byte[] readIdentificationImage(String imageToken) {
        try {
            return Files.readAllBytes(resolveIdentificationImage(imageToken).getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stored identification image", e);
        }
    }

    /**
     * 从公开 URL（如 /images/uploads/identification/xxx.jpg）还原为本地引用。
     * 用于阶段 3 按需诊断时重新定位已保存的图片。
     */
    public StoredImageReference resolveFromPublicUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalArgumentException("Public URL is blank");
        }
        // 提取文件名部分
        String filename = publicUrl;
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }
        return resolveIdentificationImage(filename);
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return DEFAULT_EXTENSION;
        }

        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == originalFilename.length() - 1) {
            return DEFAULT_EXTENSION;
        }

        return originalFilename.substring(lastDotIndex);
    }

    private String sanitizeToken(String imageToken) {
        try {
            Path tokenPath = Path.of(imageToken).getFileName();
            if (tokenPath == null) {
                throw new IllegalArgumentException("Invalid image token");
            }
            return tokenPath.toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid image token", e);
        }
    }

    public static class StoredImageReference {
        private final String token;
        private final String publicUrl;
        private final Path absolutePath;
        private final String originalFilename;

        public StoredImageReference(String token, String publicUrl, Path absolutePath, String originalFilename) {
            this.token = token;
            this.publicUrl = publicUrl;
            this.absolutePath = absolutePath;
            this.originalFilename = originalFilename;
        }

        public String getToken() {
            return token;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public Path getAbsolutePath() {
            return absolutePath;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }
    }
}
