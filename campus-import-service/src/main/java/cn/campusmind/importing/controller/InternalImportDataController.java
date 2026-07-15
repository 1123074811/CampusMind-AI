package cn.campusmind.importing.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.application.RawDocumentService;
import cn.campusmind.importing.domain.RawDocument;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/import/users/{userId}/raw-documents")
public class InternalImportDataController {

    private final RawDocumentService rawDocumentService;

    public InternalImportDataController(RawDocumentService rawDocumentService) {
        this.rawDocumentService = rawDocumentService;
    }

    @GetMapping
    public ApiResponse<List<RawDocument>> list(@PathVariable Long userId) {
        return ApiResponse.ok(rawDocumentService.listOwned(userId));
    }

    @DeleteMapping
    public ApiResponse<Long> delete(@PathVariable Long userId) {
        return ApiResponse.ok(rawDocumentService.deleteOwnedByUser(userId));
    }
}
