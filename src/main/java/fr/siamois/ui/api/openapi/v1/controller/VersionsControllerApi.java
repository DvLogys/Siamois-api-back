package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.history.EntityVersionResource;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.VersionApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/versions")
@RequiredArgsConstructor
public class VersionsControllerApi {

    private final ProjectApiService projectApiService;
    private final VersionApiService versionApiService;

    @GetMapping("/{type}/{id}")
    public ResponseEntity<ListResponse<EntityVersionResource>> listVersions(
            @PathVariable String type,
            @PathVariable Long id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        List<EntityVersionResource> versions = versionApiService.listVersions(caller, type, id);

        ListMeta meta = new ListMeta((long) versions.size(), versions.size(), 0L);
        return ResponseEntity.ok(new ListResponse<>(versions, meta));
    }
}
