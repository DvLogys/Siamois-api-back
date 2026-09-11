package fr.siamois.ui.api.openapi.v1.request.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Réponses du formulaire de la fiche d'un projet, indexées par identifiant de champ")
public class ProjectAnswersPatchRequest {

    private Map<String, Object> answers;
}
