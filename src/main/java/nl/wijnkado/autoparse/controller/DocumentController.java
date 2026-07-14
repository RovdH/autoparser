package nl.wijnkado.autoparse.controller;

import nl.wijnkado.autoparse.service.DocumentService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
public class DocumentController {

    private static final ZoneId DELIVERY_TIME_ZONE = ZoneId.of("Europe/Amsterdam");

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/orders/docx")
    public ResponseEntity<Resource> generateOrdersDocx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws Exception {
        LocalDate[] range = resolveDateRange(date, from, to);
        Path file;
        try {
            file = documentService.generateOrdersDocument(range[0], range[1]);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        byte[] bytes = Files.readAllBytes(file);
        ByteArrayResource resource = new ByteArrayResource(bytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName().toString() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(bytes.length)
                .body(resource);
    }

    private LocalDate[] resolveDateRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date != null && (from != null || to != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Gebruik óf 'date', óf 'from' en 'to'.");
        }
        if ((from == null) != (to == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Voor een range zijn zowel 'from' als 'to' verplicht.");
        }

        if (date != null) {
            return new LocalDate[]{date, date};
        }
        if (from != null) {
            if (from.isAfter(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'from' mag niet na 'to' liggen.");
            }
            return new LocalDate[]{from, to};
        }

        LocalDate tomorrow = LocalDate.now(DELIVERY_TIME_ZONE).plusDays(1);
        return new LocalDate[]{tomorrow, tomorrow};
    }
}
