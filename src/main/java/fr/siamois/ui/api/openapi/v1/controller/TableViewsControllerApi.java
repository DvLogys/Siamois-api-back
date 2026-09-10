package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.TableViewApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/table-views")
@RequiredArgsConstructor
public class TableViewsControllerApi {

    private final ProjectApiService projectApiService;
    private final TableViewApiService tableViewApiService;

    @GetMapping
    public ResponseEntity<ListResponse<UITableViewDTO>> listViews(@RequestParam String resourceType) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<UITableViewDTO> views = tableViewApiService.listOwnViews(caller, resourceType);
        return ResponseEntity.ok(new ListResponse<>(views, new ListMeta((long) views.size(), views.size(), 0L)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response<UITableViewDTO>> getView(@PathVariable Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(tableViewApiService.getOwnView(caller, id)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<UITableViewDTO>> createView(@RequestBody UITableViewDTO request) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(tableViewApiService.createView(caller, request)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<UITableViewDTO>> updateView(
            @PathVariable Long id,
            @RequestBody UITableViewDTO request) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(tableViewApiService.updateView(caller, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteView(@PathVariable Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        tableViewApiService.deleteView(caller, id);
        return ResponseEntity.noContent().build();
    }
}
