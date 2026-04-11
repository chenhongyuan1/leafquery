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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

@Service
public class LocalImageStorageService {

    private static final String DEFAULT_EXTENSION = ".jpg";
    private final Path uploadRoot;

    public LocalImageStorageService(
            @org.springframework.beans.factory.annotation.Value("${app.upload.dir:./vue-frontend/public/images/uploads}") String uploadDirPath) {
        this.uploadRoot = Path.of(uploadDirPath).toAbsolutePath().normalize();
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
                long fileSize = file.getSize();
                // 核心控制：如果图片体积超过 1MB，强行进行缩放压缩，防止把 Dify/火山 API 网关打崩
                if (fileSize > 1048576) {
                    BufferedImage originalImage = ImageIO.read(inputStream);
                    if (originalImage != null) {
                        int width = originalImage.getWidth();
                        int height = originalImage.getHeight();
                        int MAX_DIMENSION = 1024; // 锁定最大边长 1024 像素

                        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                            double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
                            width = (int) (width * scale);
                            height = (int) (height * scale);
                        }

                        // 洗掉 PNG 的空白透明通道，以 RGB 格式写入防黑边
                        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = resizedImage.createGraphics();
                        g.setColor(java.awt.Color.WHITE);
                        g.fillRect(0, 0, width, height);
                        g.drawImage(originalImage, 0, 0, width, height, null);
                        g.dispose();

                        // 强制将扩展名锁死为 .jpg，利用 JPG 的高压缩率
                        extension = ".jpg";
                        filename = UUID.randomUUID() + extension;
                        targetPath = identificationDir.resolve(filename);

                        ImageIO.write(resizedImage, "jpg", targetPath.toFile());
                    } else {
                        // 回退：如果 ImageIO 读不出（比如极其诡异的格式），则原样保存
                        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    // 低于 1MB 的图直接保存
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
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
