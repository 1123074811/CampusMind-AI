package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.importing.config.ImportProperties;
import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * OCR 图片文字提取器：基于 Tess4J（Tesseract）实现。
 * 支持 PNG, JPG, JPEG, BMP, TIFF, GIF, WEBP 格式。
 */
@Component
public class OcrTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(OcrTextExtractor.class);

    static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "bmp", "tiff", "tif", "gif", "webp"
    );

    private final ImportProperties properties;
    private Tesseract tesseract;

    public OcrTextExtractor(ImportProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        tesseract = new Tesseract();
        String tessdataPath = properties.tessdataPath();
        if (tessdataPath != null && !tessdataPath.isBlank()) {
            tesseract.setDatapath(tessdataPath);
        }
        String language = properties.ocrLanguage();
        if (language != null && !language.isBlank()) {
            tesseract.setLanguage(language);
        }
        log.info("OcrTextExtractor 初始化完成: tessdata={}, language={}", tessdataPath, language);
    }

    /**
     * 判断文件扩展名是否为支持的图片格式。
     */
    public boolean isSupported(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = fileName.substring(dot + 1).toLowerCase();
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    /**
     * 从图片字节数组中提取文字。
     *
     * @param imageBytes 图片字节数据
     * @return 提取的文本
     * @throws BusinessException OCR 失败或图片无效
     */
    public String extractText(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("OCR_FAILED", "图片数据为空", HttpStatus.BAD_REQUEST);
        }

        BufferedImage image;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
            image = ImageIO.read(bis);
        } catch (IOException ex) {
            throw new BusinessException("OCR_FAILED", "图片解析失败: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
        if (image == null) {
            throw new BusinessException("OCR_FAILED", "无法识别图片格式", HttpStatus.BAD_REQUEST);
        }

        try {
            String text = tesseract.doOCR(image);
            if (text != null) {
                text = text.trim();
            }
            return text != null ? text : "";
        } catch (TesseractException ex) {
            log.error("OCR 识别失败: {}", ex.getMessage());
            throw new BusinessException("OCR_FAILED", "OCR识别失败: " + ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
