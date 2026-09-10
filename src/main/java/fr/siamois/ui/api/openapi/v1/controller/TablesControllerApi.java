package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.table.TableCreatePolicyResource;
import fr.siamois.ui.api.openapi.v1.resource.table.TableDefinitionResource;
import fr.siamois.ui.api.openapi.v1.service.PanelResourceTypes;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.TableAccessApiService;
import fr.siamois.ui.api.openapi.v1.service.TableDefinitionApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
public class TablesControllerApi {

    private final ProjectApiService projectApiService;
    private final TableDefinitionApiService tableDefinitionApiService;
    private final TableAccessApiService tableAccessApiService;

    @GetMapping("/{type}/columns")
    public ResponseEntity<Response<TableDefinitionResource>> getColumns(
            @PathVariable String type,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        // La définition ne porte aucune donnée d'institution, mais elle décrit ce que l'application
        // sait afficher : elle reste derrière l'authentification comme le reste de /api/v1.
        projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new Response<>(tableDefinitionApiService.getDefinition(type, lang)));
    }
    /**
     * Ce que la barre d'outils de ce tableau propose de créer, vu depuis l'écran indiqué.
     *
     * @param scopeType type de la fiche dont on regarde un onglet (absent pour la liste générale)
     * @param scopeId   identifiant de cette fiche
     */
    @GetMapping("/{type}/create-policy")
    public ResponseEntity<Response<TableCreatePolicyResource>> getCreatePolicy(
            @PathVariable String type,
            @RequestParam Long organizationId,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(organizationId, caller);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PanelResourceTypes resourceType = PanelResourceTypes.fromSlug(type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Type de ressource inconnu : " + type));

        return ResponseEntity.ok(new Response<>(tableAccessApiService.getPolicy(
                resourceType, caller, institution, scopeType, scopeId, lang)));
    }
}
