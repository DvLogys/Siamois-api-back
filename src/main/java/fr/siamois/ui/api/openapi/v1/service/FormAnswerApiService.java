package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.*;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Les réponses d'un formulaire, telles que l'API les expose, pour n'importe quelle entité.
 *
 * Toutes les fiches du JSF rendent le même formulaire dynamique — {@code SpatialUnit.DETAILS_FORM},
 * {@code ActionUnit.DETAILS_FORM}, celui de l'UE… — et la conversion d'une réponse persistée vers
 * sa forme d'API ne dépend que du type de réponse, jamais de l'entité qui la porte. Elle vivait
 * pourtant recopiée dans deux services ; elle est ici, une fois, pour que toute fiche puisse être
 * lue et modifiée par le même chemin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormAnswerApiService {

    private static final String CONCEPTS = "concepts";

    private final FormService formService;
    private final LabelService labelService;
    private final FieldResourceApiMapper fieldResourceApiMapper;

    /**
     * Les réponses d'une entité pour les champs d'un formulaire.
     *
     * Si la lecture des valeurs échoue, les champs sont tout de même décrits, sans valeur : une
     * fiche sans réponses reste lisible, une fiche sans champs ne l'est pas.
     *
     * @param dto         l'entité dont on lit les réponses
     * @param fieldSource les champs à lire
     * @param locale      la langue des libellés
     * @return les réponses, indexées par identifiant de champ
     */
    public Map<String, FieldAnswer> buildAnswers(AbstractEntityDTO dto, FieldSource fieldSource, Locale locale) {
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            return toFieldsMap(response, fieldSource, locale);
        } catch (RuntimeException ex) {
            log.warn("Impossible de construire les réponses formulaire pour l'entité id={} "
                    + "(repli sur les métadonnées seules) : {}", dto.getId(), ex.toString(), ex);
            return buildNullAnswersMap(fieldSource, locale);
        }
    }

    /** Les champs d'un formulaire, sans leurs valeurs. */
    public Map<String, FieldResource> buildFieldsMetadataOnly(FieldSource fieldSource, Locale locale) {
        Map<String, FieldResource> fields = new LinkedHashMap<>();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            fields.put(String.valueOf(field.getId()), toFieldResource(field, locale));
        }
        return fields;
    }

    private Map<String, FieldAnswer> toFieldsMap(CustomFormResponseViewModel response, FieldSource fallback, Locale locale) {
        if (response.getAnswers() == null) return buildNullAnswersMap(fallback, locale);
        Map<String, FieldAnswer> out = new LinkedHashMap<>();
        String lang = locale.getLanguage();
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> e : response.getAnswers().entrySet()) {
            CustomField field = e.getKey();
            out.put(String.valueOf(field.getId()),
                    toTypedAnswer(FieldResourceApiMapper.answerTypeOf(field), toFieldResource(field, locale),
                            formService.readAnswerValueForApi(e.getValue()), lang));
        }
        return out;
    }

    private Map<String, FieldAnswer> buildNullAnswersMap(FieldSource fieldSource, Locale locale) {
        Map<String, FieldAnswer> out = new LinkedHashMap<>();
        String lang = locale.getLanguage();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            out.put(String.valueOf(field.getId()),
                    toTypedAnswer(FieldResourceApiMapper.answerTypeOf(field), toFieldResource(field, locale), null, lang));
        }
        return out;
    }

    private FieldResource toFieldResource(CustomField field, Locale locale) {
        return fieldResourceApiMapper.toFieldResource(field, locale);
    }

    /**
     * La réponse persistée d'un champ du formulaire, retrouvée par identifiant : les instances de
     * {@link CustomField} du formulaire et celles de la réponse peuvent différer.
     */
    public CustomFieldAnswerViewModel findViewModelForField(Map<CustomField, CustomFieldAnswerViewModel> answers,
                                                            CustomField field) {
        CustomFieldAnswerViewModel direct = answers.get(field);
        if (direct != null) {
            return direct;
        }
        if (field == null || field.getId() == null) {
            return null;
        }
        return answers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && field.getId().equals(entry.getKey().getId()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public FieldAnswer toTypedAnswer(String answerType, FieldResource field, Object raw, String lang) {
        return switch (answerType) {
            case "TEXT" -> new TextFieldAnswer(answerType, field, raw instanceof String s ? s : null);
            case "INTEGER" -> new IntegerFieldAnswer(answerType, field, raw instanceof Integer i ? i : null);
            case "DATETIME" -> new DateFieldAnswer(answerType, field, raw instanceof OffsetDateTime dt ? dt : null);
            case "SELECT_ONE_FROM_FIELD_CODE", "SELECT_ONE_PERSON", "SELECT_ONE_ACTION_UNIT",
                 "SELECT_ONE_SPATIAL_UNIT", "SELECT_ONE_ACTION_CODE", "SELECT_ONE_RECORDING_UNIT",
                 "SELECT_ADDRESS", "SELECT_ONE" ->
                    new SelectOneFieldAnswer(answerType, field, toResourceRef(answerType, raw, lang));
            case "SELECT_MULTIPLE_PERSON", "SELECT_MULTIPLE_FROM_FIELD_CODE",
                 "SELECT_MULTIPLE_RECORDING_UNIT", "SELECT_MULTIPLE_SPATIAL_UNIT_TREE",
                 "SELECT_MULTIPLE_SPECIMEN", "SELECT_MULTIPLE_CONTAINER",
                 "SELECT_MULTIPLE_PHASE", "SELECT_MULTIPLE" ->
                    new SelectManyFieldAnswer(answerType, field, toResourceRefList(answerType, raw, lang));
            case "MEASUREMENT" -> new MeasurementFieldAnswer(answerType, field, toMeasurementRef(raw));
            default -> new TextFieldAnswer(answerType, field, raw != null ? raw.toString() : null);
        };
    }

    private ResourceRef toResourceRef(String answerType, Object raw, String lang) {
        if (raw == null) return null;
        return switch (answerType) {
            case "SELECT_ONE_FROM_FIELD_CODE" -> conceptResourceRef(raw, lang);
            case "SELECT_ONE_PERSON" -> {
                if (raw instanceof PersonDTO p)
                    yield new ResourceRef(String.valueOf(p.getId()), "persons", p.displayName());
                yield null;
            }
            case "SELECT_ONE_ACTION_UNIT" -> {
                if (raw instanceof ActionUnitDTO a)
                    yield new ResourceRef(String.valueOf(a.getId()), "action-units", a.getName());
                // Les deux formes coexistent : une UE porte le résumé de son projet, jamais le DTO
                // complet — sans ce cas, la cellule « Projet » restait vide alors que la ligne le
                // connaît. Les deux classes descendent d'AbstractEntityDTO sans se dériver l'une
                // l'autre, d'où le second test.
                if (raw instanceof ActionUnitSummaryDTO a)
                    yield new ResourceRef(String.valueOf(a.getId()), "action-units", a.getName());
                yield null;
            }
            case "SELECT_ONE_SPATIAL_UNIT" -> {
                if (raw instanceof SpatialUnitSummaryDTO s)
                    yield new ResourceRef(String.valueOf(s.getId()), "spatial-units", s.getName());
                yield null;
            }
            case "SELECT_ONE_ACTION_CODE" -> {
                if (raw instanceof ActionCodeDTO ac)
                    yield new ResourceRef(String.valueOf(ac.getId()), "action-codes", ac.getCode());
                yield null;
            }
            case "SELECT_ONE_RECORDING_UNIT" -> {
                if (raw instanceof RecordingUnitSummaryDTO r)
                    yield new ResourceRef(String.valueOf(r.getId()), "recording-units", r.getFullIdentifier());
                yield null;
            }
            default -> null;
        };
    }

    private ResourceRef conceptResourceRef(Object raw, String lang) {
        ConceptDTO concept = null;
        String preferredLabel = null;
        if (raw instanceof ConceptDTO c) {
            concept = c;
        } else if (raw instanceof ConceptAutocompleteDTO ac) {
            concept = ac.concept();
            if (ac.getConceptLabelToDisplay() != null) {
                preferredLabel = ac.getConceptLabelToDisplay().getLabel();
            }
            if ((preferredLabel == null || preferredLabel.isBlank()) && ac.getOriginalPrefLabel() != null) {
                preferredLabel = ac.getOriginalPrefLabel();
            }
        }
        if (concept == null) {
            return null;
        }
        String label = preferredLabel;
        if (label == null || label.isBlank()) {
            try {
                label = labelService.findLabelOf(concept, lang).getLabel();
            } catch (RuntimeException ignored) {
                label = null;
            }
        }
        if (label == null || label.isBlank()) {
            label = concept.getExternalId();
        }
        return new ResourceRef(String.valueOf(concept.getId()), CONCEPTS, label);
    }

    private List<ResourceRef> toResourceRefList(String answerType, Object raw, String lang) {
        if (raw == null) return null;
        Collection<?> col = raw instanceof Collection<?> c ? c : List.of(raw);
        return col.stream()
                .map(item -> toResourceRefFromItem(answerType, item, lang))
                .filter(Objects::nonNull)
                .toList();
    }

    private ResourceRef toResourceRefFromItem(String answerType, Object item, String lang) {
        if (item instanceof PersonDTO p)
            return new ResourceRef(String.valueOf(p.getId()), "persons", p.displayName());
        if (item instanceof ConceptDTO || item instanceof ConceptAutocompleteDTO)
            return conceptResourceRef(item, lang);
        if (item instanceof PhaseDTO p) {
            String label = p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : p.getIdentifier();
            return new ResourceRef(String.valueOf(p.getId()), "phases", label);
        }
        if (item instanceof SpatialUnitSummaryDTO s)
            return new ResourceRef(String.valueOf(s.getId()), "spatial-units", s.getName());
        if (item instanceof RecordingUnitSummaryDTO r)
            return new ResourceRef(String.valueOf(r.getId()), "recording-units", r.getFullIdentifier());
        if (item instanceof AbstractEntityDTO e)
            return new ResourceRef(String.valueOf(e.getId()), answerType.toLowerCase(), null);
        return null;
    }

    private MeasurementRef toMeasurementRef(Object raw) {
        if (raw instanceof MeasurementAnswerDTO m) {
            String symbol = m.getUnit() != null ? m.getUnit().getSymbol() : null;
            return new MeasurementRef(m.getNumericValue(), symbol, m.getNormalizedValue(), m.getComment());
        }
        return null;
    }
}
