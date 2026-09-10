package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationProjectsControllerApi {

    private static final String HEADER_TOTAL_COUNT = "X-Total-Count";

    private final ProjectApiService projectApiService;
    private final ProjectResponseMapper projectResponseMapper;

    @GetMapping("/{id}/projects")
    public ResponseEntity<ProjectListResponse> getProjects(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();

        // pageAccessibleProjects applique le contrôle de périmètre sur l'organisation.
        Page<AccessibleProjectForApi> rows = projectApiService.pageAccessibleProjects(
                caller, id, search, offset, limit, sort);

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        List<ProjectResource> resources = rows.getContent().stream()
                .map(row -> projectResponseMapper.toResource(row, lang))
                .toList();

        ListMeta meta = new ListMeta(rows.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(rows.getTotalElements()))
                .body(new ProjectListResponse(resources, meta));
    }
}
