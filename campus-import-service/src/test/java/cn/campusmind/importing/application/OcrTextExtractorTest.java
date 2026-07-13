package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.importing.config.ImportProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OcrTextExtractor 测试。
 * 注意：完整 OCR 识别测试需要安装 Tesseract 和 tessdata，这里主要测试异常场景。
 */
class OcrTextExtractorTest {

    private ImportProperties createProperties() {
        return new ImportProperties(
                10,                        // rainCookieTtlMinutes
                false,                     // rainCookieEnabled
                2097152,                   // maxRainJsonBytes
                7,                         // rawDocumentRetentionDays
                20000,                     // maxTextLength
                5242880,                   // maxImageBytes
                10485760,                  // maxFileBytes
                5,                         // aiConnectTimeoutSeconds
                30,                        // aiReadTimeoutSeconds
                10,                        // rateLimitPerMinute
                "test-tessdata",           // tessdataPath
                "chi_sim+eng"              // ocrLanguage
        );
    }

    @Test
    void shouldThrowOnEmptyImageBytes() {
        ImportProperties props = createProperties();
        OcrTextExtractor extractor = new OcrTextExtractor(props);
        extractor.init();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                extractor.extractText(new byte[0]));

        assertEquals("OCR_FAILED", ex.getCode());
        assertTrue(ex.getMessage().contains("图片数据为空"));
    }

    @Test
    void shouldThrowOnNullImageBytes() {
        ImportProperties props = createProperties();
        OcrTextExtractor extractor = new OcrTextExtractor(props);
        extractor.init();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                extractor.extractText(null));

        assertEquals("OCR_FAILED", ex.getCode());
    }

    @Test
    void shouldRecognizeSupportedExtensions() {
        ImportProperties props = createProperties();
        OcrTextExtractor extractor = new OcrTextExtractor(props);

        assertTrue(extractor.isSupported("poster.png"));
        assertTrue(extractor.isSupported("photo.jpg"));
        assertTrue(extractor.isSupported("photo.jpeg"));
        assertTrue(extractor.isSupported("scan.bmp"));
        assertTrue(extractor.isSupported("doc.tiff"));
        assertTrue(extractor.isSupported("doc.tif"));
    }

    @Test
    void shouldRejectUnsupportedExtensions() {
        ImportProperties props = createProperties();
        OcrTextExtractor extractor = new OcrTextExtractor(props);

        assertTrue(!extractor.isSupported("file.pdf"));
        assertTrue(!extractor.isSupported("file.docx"));
        assertTrue(!extractor.isSupported("file.txt"));
        assertTrue(!extractor.isSupported("file.xlsx"));
        assertTrue(!extractor.isSupported("file.csv"));
        assertTrue(!extractor.isSupported("noextension"));
    }

    @Test
    void shouldHandleNullFileName() {
        ImportProperties props = createProperties();
        OcrTextExtractor extractor = new OcrTextExtractor(props);

        assertTrue(!extractor.isSupported(null));
    }

    @Test
    void shouldInitTesseractWithConfig() {
        ImportProperties props = createProperties();
        OcrTextExtractor extractor = new OcrTextExtractor(props);

        // init() 应不抛异常
        extractor.init();

        // 确认对象创建成功
        assertNotNull(extractor);
    }
}
