package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.search.SearchResultItemResource;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.SearchApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchControllerApi {

    private final ProjectApiService projectApiService;
    private final SearchApiService searchApiService;

    @GetMapping
    public ResponseEntity<ListResponse<SearchResultItemResource>> search(
            @RequestParam Long organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        List<SearchResultItemResource> results =
                searchApiService.searchInOrganization(caller, organizationId, q, limit);

        ListMeta meta = new ListMeta((long) results.size(), results.size(), 0L);
        return ResponseEntity.ok(new ListResponse<>(results, meta));
    }
}
