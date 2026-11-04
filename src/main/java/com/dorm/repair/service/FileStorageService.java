package com.dorm.repair.service;

import com.dorm.repair.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 报修图片的本地存储。
 * <p>
 * 图片保存在 application.yml 中 app.upload-dir 指定的目录下，按日期分子目录，文件名用 UUID，
 * 数据库只保存相对路径（如 20261104/xxx.jpg）。页面通过 /uploads/相对路径 访问（见 WebConfig）。
 */
@Service
public class FileStorageService {

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** PNG 文件固定的 8 字节文件头 */
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final Path rootDir;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建上传目录: " + rootDir, e);
        }
    }

    /** 上传根目录（绝对路径） */
    public Path getRootDir() {
        return rootDir;
    }

    /**
     * 保存图片。
     *
     * @param file 用户上传的文件，可以为空（图片是可选项）
     * @return 相对路径；没有上传文件时返回 null
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("仅支持 jpg / png / gif / webp 格式的图片");
        }
        // 扩展名可以随便改，所以再检查文件头（魔数），确认内容真的是图片
        if (!looksLikeImage(file)) {
            throw new BusinessException("文件内容不是有效的图片");
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = rootDir.resolve(dateDir);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException("图片保存失败，请稍后重试");
        }
        return dateDir + "/" + fileName;
    }

    /** 删除图片（工单保存失败时清理已上传的文件） */
    public void delete(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            Path target = rootDir.resolve(relativePath).normalize();
            // 防御：只允许删除上传目录内的文件
            if (target.startsWith(rootDir)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    /** 根据文件头判断是否为 JPEG / PNG / GIF / WEBP 图片 */
    private boolean looksLikeImage(MultipartFile file) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        } catch (IOException e) {
            return false;
        }
        if (head.length < 12) {
            return false;
        }
        boolean jpeg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        boolean png = Arrays.equals(Arrays.copyOf(head, 8), PNG_HEADER);
        String ascii = new String(head, StandardCharsets.ISO_8859_1);
        boolean gif = ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a");
        boolean webp = ascii.startsWith("RIFF") && ascii.startsWith("WEBP", 8);
        return jpeg || png || gif || webp;
    }
}
