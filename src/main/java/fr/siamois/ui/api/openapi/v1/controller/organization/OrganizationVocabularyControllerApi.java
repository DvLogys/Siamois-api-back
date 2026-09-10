package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.request.vocabulary.ThesaurusConfigRequest;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import fr.siamois.ui.api.openapi.v1.service.OrganizationVocabularyApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{id}")
@RequiredArgsConstructor
public class OrganizationVocabularyControllerApi {

    private final ProjectApiService projectApiService;
    private final OrganizationVocabularyApiService organizationVocabularyApiService;

    @GetMapping("/concepts")
    public ResponseEntity<Response<List<ResolvedConceptResource>>> getConcepts(
            @PathVariable Long id,
            @RequestParam String fieldCode,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new Response<>(
                organizationVocabularyApiService.listConcepts(caller, id, fieldCode, q, limit, offset, lang)));
    }

    @GetMapping("/field-codes")
    public ResponseEntity<Response<List<String>>> getFieldCodes(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new Response<>(
                organizationVocabularyApiService.listFieldCodes(caller, id, lang)));
    }

    @PutMapping(value = "/thesaurus", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<VocabularyResource>> configureThesaurus(
            @PathVariable Long id,
            @RequestBody ThesaurusConfigRequest request) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(
                organizationVocabularyApiService.configureThesaurus(caller, id, request)));
    }
}
