package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormLayoutPanelResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitResource extends RecordingUnitResourceIdentifier {

    @Schema(description = "Révision de synchronisation (optimistic locking)")
    private Long syncRevision;

    private String identifier;
    private String fullIdentifier;
    private String projectId;

    private ResolvedConceptResource type;

    // Ce que les colonnes visibles par défaut du tableau affichent. Toutes ces valeurs sont déjà
    // portées par le DTO que la recherche renvoie : les exposer ne coûte aucune requête de plus.
    // Les colonnes adossées à un concept (cycle, agent, interprétation) restent absentes — masquées
    // par défaut, elles demanderaient une résolution de libellé par ligne.
    @Schema(description = "Projet de rattachement")
    @Nullable
    private ResourceRef actionUnit;

    @Schema(description = "Lieu de rattachement")
    @Nullable
    private ResourceRef spatialUnit;

    @Schema(description = "Auteur de l'enregistrement")
    @Nullable
    private ResourceRef author;

    @Nullable
    private String matrixColor;

    @Nullable
    private OffsetDateTime openingDate;

    @Nullable
    private OffsetDateTime closingDate;

    @Nullable
    private Integer tpq;

    @Nullable
    private Integer taq;

    @Schema(description = "Géométrie de l'UE.")
    @Nullable
    private GeometryDTO geom;

    @Schema(description = "Valeurs de tous les champs formulaire (système et custom), indexées par fieldId. "
            + "Chaque entrée embarque sa définition (label, answerType, hint, etc.).")
    private Map<String, FieldAnswer> answers;

    @Schema(description = "Sections, lignes et grille du formulaire — sans quoi les champs ne peuvent qu'être empilés")
    private List<FormLayoutPanelResource> layout;

    @Schema(description = "Vrai si l'appelant peut modifier cette ligne. Résolu pour toute la page en une "
            + "requête, comme le fait le tableau JSF, et non une fois par ligne.")
    @Nullable
    private Boolean canEdit;

    @JsonProperty("_counts")
    private RecordingUnitResourceCounts count;

    @JsonProperty("_links")
    private RecordingUnitResourceLinks links;

}
