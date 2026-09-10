package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.i18n.LanguagesResource;
import fr.siamois.ui.api.openapi.v1.service.I18nApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/i18n")
@RequiredArgsConstructor
public class I18nControllerApi {

    private final I18nApiService i18nApiService;

    @GetMapping("/languages")
    public ResponseEntity<Response<LanguagesResource>> getAvailableLanguages() {
        return ResponseEntity.ok(new Response<>(i18nApiService.availableLanguages()));
    }

    @GetMapping("/messages")
    public ResponseEntity<Response<Map<String, String>>> getMessages(
            @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(new Response<>(i18nApiService.messagesFor(lang)));
    }
}
