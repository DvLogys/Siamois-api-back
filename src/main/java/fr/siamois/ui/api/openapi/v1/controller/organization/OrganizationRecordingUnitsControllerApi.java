package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.table.definitions.RecordingUnitTableDefinitionFactory;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.PanelResourceTypes;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.TableAccessApiService;
import fr.siamois.ui.api.openapi.v1.service.TableDefinitionApiService;
import fr.siamois.ui.api.openapi.v1.service.TableQueryApiService;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationRecordingUnitsControllerApi {

    private static final String HEADER_TOTAL_COUNT = "X-Total-Count";

    /**
     * Colonnes dont la valeur est une collection que la recherche ne ramène pas d'office : la
     * demander coûte trois requêtes groupées pour la page, elle n'est donc faite que si l'une de
     * ces colonnes est affichée.
     */
    private static final Set<String> COLUMNS_NEEDING_FULL_RELATIONS = Set.of("parents", "children", "phases");

    /** Les dernières UE enregistrées d'abord, comme la liste par projet. */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "creationTime")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final RecordingUnitService recordingUnitService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final ProjectApiService projectApiService;
    private final TableQueryApiService tableQueryApiService;
    private final TableAccessApiService tableAccessApiService;
    private final TableDefinitionApiService tableDefinitionApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;


    @GetMapping("/{id}/recording-units")
    @Operation(summary = "Liste paginée des unités d'enregistrement d'une institution")
    @Parameter(name = "filter", in = ParameterIn.QUERY,
            description = "Filtre de colonne répétable, ex. filter=type:12,15 — colonnes filtrables de "
                    + "GET /api/v1/tables/recording-unit/columns",
            array = @ArraySchema(schema = @Schema(type = "string")))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Institution non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitListResponse> getRecordingUnits(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tri, ex. fullIdentifier:asc — colonnes triables de GET /api/v1/tables/recording-unit/columns")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Recherche libre sur l'identifiant complet")
            @RequestParam(required = false) String q,
            @Parameter(description = "Colonnes affichées, dont les réponses de formulaire sont à construire. "
                    + "Absent, aucune n'est construite : une liste qui ne sert qu'à naviguer n'en a pas besoin.")
            @RequestParam(required = false) List<String> columns,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            HttpServletRequest request) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(id, caller);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        // Tri et filtres sont lus à travers la définition des colonnes du type : la liste n'accepte
        // donc que ce que cette définition annonce au client comme triable ou filtrable.
        FilterDTO filters = tableQueryApiService.filtersOf(PanelResourceTypes.RECORDING_UNIT, rawFilters(request));
        if (q != null && !q.isBlank()) {
            filters.add(RecordingUnitSpec.FULL_IDENTIFIER, q.trim(), FilterDTO.FilterType.CONTAINS);
        }
        Sort order = tableQueryApiService.sortOf(PanelResourceTypes.RECORDING_UNIT, sort, DEFAULT_SORT);

        int pageNumber = limit > 0 ? offset / limit : 0;
        Pageable pageable = PageRequest.of(pageNumber, limit, order);

        // Les colonnes « Fait partie de », « Contient » et « Phases » affichent une collection : sans
        // elle, leurs cellules seraient vides alors que la ligne a bien des voisines.
        boolean needsFullRelations = !Collections.disjoint(
                tableDefinitionApiService.valueBindingsOf(PanelResourceTypes.RECORDING_UNIT, columns),
                COLUMNS_NEEDING_FULL_RELATIONS);
        Page<RecordingUnitDTO> page = recordingUnitService.searchRecordingUnit(
                institution, filters, pageable, needsFullRelations);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(recordingUnitResourceMapper::convert)
                .toList();
        // Les cellules du tableau sont éditables : chaque ligne porte donc les réponses des champs
        // de ses colonnes, et le droit de la modifier. Les deux sont résolus pour toute la page.
        enrichEditableRows(page.getContent(), resources, columns, caller, institution, lang);

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(page.getTotalElements()))
                .body(new RecordingUnitListResponse(resources, meta));
    }

    /**
     * Les valeurs brutes du paramètre {@code filter}, telles que la requête les porte.
     *
     * Spring convertirait un {@code List<String>} en découpant chaque valeur sur les virgules, ce
     * qui casserait aussi bien une sélection multiple ({@code type:12,15}) qu'un texte contenant
     * une virgule. Le paramètre reste déclaré pour la documentation et la validation, mais c'est
     * cette lecture-ci qui est utilisée.
     */
    private static List<String> rawFilters(HttpServletRequest request) {
        String[] values = request.getParameterValues("filter");
        return values == null ? List.of() : List.of(values);
    }
    /**
     * Complète les lignes de la page de quoi être éditées sur place : les réponses des champs des
     * colonnes affichées, et le droit de modifier chaque ligne.
     *
     * Le droit se calcule toujours — une requête groupée pour la page entière — mais les réponses
     * ne se construisent que pour les colonnes demandées : c'est de loin le poste le plus lourd.
     */
    private void enrichEditableRows(List<RecordingUnitDTO> rows, List<RecordingUnitResource> resources,
                                    List<String> columns, ProjectApiCaller caller, InstitutionDTO institution,
                                    String lang) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, Map<String, FieldAnswer>> answersByRow = recordingUnitOpenApiService.buildTableAnswers(
                rows, RecordingUnitTableDefinitionFactory.definition(), columns, caller.person(), lang);

        Map<Long, Long> projectIdByRow = rows.stream()
                .filter(row -> row.getId() != null)
                .collect(HashMap::new,
                        (map, row) -> map.put(row.getId(), row.getActionUnit() != null ? row.getActionUnit().getId() : null),
                        HashMap::putAll);
        Set<Long> editable = tableAccessApiService.editableRowIds(PanelResourceTypes.RECORDING_UNIT,
                projectIdByRow, new UserInfo(institution, caller.person(), lang));

        for (int i = 0; i < rows.size(); i++) {
            Long rowId = rows.get(i).getId();
            RecordingUnitResource resource = resources.get(i);
            resource.setAnswers(answersByRow.getOrDefault(rowId, Map.of()));
            resource.setCanEdit(editable.contains(rowId));
        }
    }
}
