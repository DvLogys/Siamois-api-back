package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.LangService;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import jakarta.persistence.DiscriminatorValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * La définition d'un champ telle que l'API l'expose, quel que soit l'endroit d'où elle vient : une
 * réponse de fiche la porte pour dire ce qu'elle contient, une colonne de tableau pour dire ce
 * qu'elle affiche et comment le filtrer.
 *
 * Les deux doivent décrire un même champ à l'identique — même identifiant, même type de réponse,
 * même code de vocabulaire — sans quoi le client ne relierait pas la cellule d'une liste à la
 * réponse de la fiche qu'elle ouvre.
 */
@Service
@RequiredArgsConstructor
public class FieldResourceApiMapper {

    private final LangService langService;

    public FieldResource toFieldResource(CustomField field, Locale locale) {
        return new FieldResource(
                String.valueOf(field.getId()),
                "fields",
                langService.resolveMessage(field.getLabel(), locale),
                answerTypeOf(field),
                langService.resolveMessage(field.getHint(), locale),
                field.getIsSystemField(),
                field.getValueBinding(),
                fieldCodeOf(field),
                chipStyleOf(field),
                field.getIcon());
    }

    /**
     * Le type de réponse d'un champ, qui est le discriminant de sa sous-classe : c'est lui que le
     * client lit pour choisir le composant de saisie, aussi bien dans un formulaire que dans une
     * cellule ou une case de filtre.
     */
    public static String answerTypeOf(CustomField field) {
        DiscriminatorValue discriminator = field.getClass().getAnnotation(DiscriminatorValue.class);
        return discriminator != null ? discriminator.value() : field.getClass().getSimpleName();
    }

    /**
     * La classe CSS de la pastille qui affiche la valeur d'un champ en lecture.
     *
     * Seuls les champs de vocabulaire en déclarent une : c'est le concept sélectionné que le JSF
     * rend en pastille (voir {@code conceptChip.xhtml}), les autres valeurs s'écrivant en clair.
     */
    public static String chipStyleOf(CustomField field) {
        if (field instanceof CustomFieldSelectOneFromFieldCode one) {
            return one.getStyleClass();
        }
        if (field instanceof CustomFieldSelectMultipleFromFieldCode multiple) {
            return multiple.getStyleClass();
        }
        return null;
    }

    /** Le vocabulaire interrogé par un champ de sélection, s'il en interroge un. */
    public static String fieldCodeOf(CustomField field) {
        if (field instanceof CustomFieldSelectOneFromFieldCode one) {
            return one.getFieldCode();
        }
        if (field instanceof CustomFieldSelectMultipleFromFieldCode multiple) {
            return multiple.getFieldCode();
        }
        return null;
    }
}
