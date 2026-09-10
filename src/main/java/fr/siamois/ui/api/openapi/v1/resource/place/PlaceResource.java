package fr.siamois.ui.api.openapi.v1.resource.place;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.form.FormLayoutPanelResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
@Data
@NoArgsConstructor
public class PlaceResource extends PlaceResourceIdentifier {

    private String name;

    private Integer placeNumber;

    private ResolvedConceptResource type;

    private OrganizationResourceIdentifier organization;

    private GeometryDTO geom;

    @Schema(description = "Valeurs de tous les champs du formulaire de la fiche, indexées par fieldId. "
            + "Chaque entrée embarque sa définition (libellé, type de réponse, aide).")
    private Map<String, FieldAnswer> answers;

    @Schema(description = "Sections, lignes et grille du formulaire — sans quoi les champs ne peuvent qu'être empilés")
    private List<FormLayoutPanelResource> layout;

    @JsonProperty("_counts")
    private PlaceResourceCounts count;

    @JsonProperty("_links")
    private PlaceResourceLinks links;

}
