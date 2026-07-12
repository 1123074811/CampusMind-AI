package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 文件文本提取器：支持 PDF、DOCX、TXT、XLSX 文件的纯文本提取。
 */
@Component
public class FileTextExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt", "xlsx",
            "png", "jpg", "jpeg", "bmp", "tiff", "tif");
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/png", "image/jpeg", "image/bmp", "image/tiff"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "tiff", "tif");

    private final OcrTextExtractor ocrTextExtractor;

    public FileTextExtractor(OcrTextExtractor ocrTextExtractor) {
        this.ocrTextExtractor = ocrTextExtractor;
    }

    public boolean isSupported(String fileName) {
        String ext = getExtension(fileName);
        return SUPPORTED_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 从上传文件中提取纯文本。
     * 支持 PDF、DOCX、TXT、XLSX 和图片文件（通过 OCR 提取）。
     *
     * @param file 上传文件
     * @return 提取的纯文本
     * @throws BusinessException 文件类型不支持或解析失败
     */
    public String extractText(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException("FILE_NAME_REQUIRED", "文件名不能为空", HttpStatus.BAD_REQUEST);
        }

        String ext = getExtension(fileName).toLowerCase();
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("FILE_TYPE_UNSUPPORTED",
                    "不支持的文件类型：" + ext + "，仅支持 PDF、DOCX、TXT、XLSX 和图片文件",
                    HttpStatus.BAD_REQUEST);
        }

        try {
            return switch (ext) {
                case "pdf" -> extractFromPdf(file);
                case "docx" -> extractFromDocx(file);
                case "txt" -> extractFromTxt(file);
                case "xlsx" -> extractFromXlsx(file);
                default -> {
                    if (IMAGE_EXTENSIONS.contains(ext)) {
                        yield ocrTextExtractor.extractText(file.getBytes());
                    }
                    throw new BusinessException("FILE_TYPE_UNSUPPORTED",
                            "不支持的文件类型：" + ext, HttpStatus.BAD_REQUEST);
                }
            };
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("FILE_EXTRACT_FAILED",
                    "文件文本提取失败：" + ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String extractFromPdf(MultipartFile file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                throw new BusinessException("PDF_EMPTY", "PDF文件中未提取到文本内容", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return text.trim();
        }
    }

    private String extractFromDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XWPFDocument doc = new XWPFDocument(is)) {
            List<String> paragraphs = new ArrayList<>();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    paragraphs.add(text.trim());
                }
            }
            if (paragraphs.isEmpty()) {
                throw new BusinessException("DOCX_EMPTY", "DOCX文件中未提取到文本内容", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return String.join("\n", paragraphs);
        }
    }

    private String extractFromTxt(MultipartFile file) throws Exception {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new BusinessException("TXT_EMPTY", "TXT文件内容为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return text.trim();
    }

    private String extractFromXlsx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(is)) {
            List<String> rows = new ArrayList<>();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                if (workbook.getNumberOfSheets() > 1) {
                    rows.add("[" + sheetName + "]");
                }
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    XSSFRow row = sheet.getRow(r);
                    if (row == null) continue;
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        var cell = row.getCell(c);
                        if (cell != null) {
                            cells.add(cell.toString().trim());
                        }
                    }
                    String line = String.join(" | ", cells);
                    if (!line.isBlank()) {
                        rows.add(line);
                    }
                }
            }
            if (rows.isEmpty()) {
                throw new BusinessException("XLSX_EMPTY", "XLSX文件中未提取到内容", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return String.join("\n", rows);
        }
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }
}
