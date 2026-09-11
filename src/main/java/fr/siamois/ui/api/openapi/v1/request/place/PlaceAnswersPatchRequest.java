package fr.siamois.ui.api.openapi.v1.request.place;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Réponses du formulaire de la fiche d'un lieu, indexées par identifiant de champ")
public class PlaceAnswersPatchRequest {

    private Map<String, Object> answers;
}
