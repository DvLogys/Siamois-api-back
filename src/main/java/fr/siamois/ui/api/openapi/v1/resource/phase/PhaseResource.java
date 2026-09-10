package fr.siamois.ui.api.openapi.v1.resource.phase;

import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Phase chronologique d'un projet")
public class PhaseResource {

    @Schema(description = "Type de ressource", example = "phases")
    private String resourceType = "phases";

    @Schema(description = "Identifiant technique (phase_id)")
    private String id;

    @Schema(description = "Identifiant métier de la phase")
    private String identifier;

    @Schema(description = "Titre de la phase")
    private String title;

    @Schema(description = "Libellé d'affichage (titre ou identifiant)")
    private String label;

    @Schema(description = "Description de la phase")
    private String description;

    @Schema(description = "Rang de la phase dans la chronologie du projet")
    private Integer orderNumber;

    @Schema(description = "Borne chronologique inférieure")
    private Integer lowerBound;

    @Schema(description = "Borne chronologique supérieure")
    private Integer upperBound;

    @Schema(description = "Identifiant du projet (action unit) portant la phase")
    private Long projectId;

    @Schema(description = "Type de phase")
    private ResolvedConceptResource type;

    @Schema(description = "Périodes chronologiques associées")
    private List<ResolvedConceptResource> periods;

    @Schema(description = "Mots-clés associés")
    private List<ResolvedConceptResource> keywords;
}
