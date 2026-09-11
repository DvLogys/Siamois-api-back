package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.FieldAnswerMaps;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * L'écriture d'un formulaire : convertir les valeurs reçues du client vers ce que chaque type de
 * champ attend, et les poser sur les réponses de la fiche.
 *
 * Le pendant de {@link FormAnswerApiService}, qui fait la lecture. Toutes les fiches rendent le
 * même formulaire dynamique — UE, mobilier, projet, lieu, phase — et la conversion ne dépend que du
 * type du champ : elle vit donc ici plutôt que recopiée dans chaque service de fiche.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormAnswerWriteApiService {

    private final FormAnswerApiService formAnswerApiService;
    private final FormService formService;
    private final ConceptRepository conceptRepository;
    private final ConceptMapper conceptMapper;
    private final PersonService personService;
    private final PersonMapper personMapper;
    private final ActionUnitService actionUnitService;
    private final SpatialUnitService spatialUnitService;

    /**
     * Applique les réponses reçues sur celles d'une fiche, en convertissant chaque valeur selon le
     * type de son champ.
     *
     * Le client envoie des valeurs de JSON — un nombre, une chaîne, un objet {@code {id}} pour une
     * référence — et chaque type de champ attend autre chose : un concept, une personne, un projet.
     * La conversion se fait donc ici, une fois, pour toutes les fiches : elles rendent le même
     * formulaire dynamique et n'ont aucune raison de la refaire chacune.
     *
     * Une valeur qu'on ne sait pas convertir est ignorée avec une trace, et ne fait pas échouer le
     * reste de l'enregistrement.
     *
     * @param response    les réponses de la fiche, telles que le service de formulaire les tient
     * @param fieldSource les champs du formulaire
     * @param fieldAnswers les valeurs reçues, indexées par identifiant de champ
     */
    public void applyAnswers(CustomFormResponseViewModel response,
                             FieldSource fieldSource,
                             Map<String, Object> fieldAnswers) {
        if (fieldAnswers == null || fieldAnswers.isEmpty() || response.getAnswers() == null) {
            return;
        }
        for (Map.Entry<String, Object> e : fieldAnswers.entrySet()) {
            mergeOneFieldAnswer(response, fieldSource, e.getKey(), e.getValue());
        }
    }

    private void mergeOneFieldAnswer(CustomFormResponseViewModel response,
                                     FieldSource fieldSource,
                                     String key,
                                     Object value) {
        long fieldId;
        try {
            fieldId = Long.parseLong(key);
        } catch (NumberFormatException ex) {
            log.debug("Clé de champ ignorée (non numérique): {}", key);
            return;
        }
        CustomField field = fieldSource.findFieldById(fieldId);
        if (field == null) {
            log.debug("Champ id={} absent du formulaire effectif", fieldId);
            return;
        }
        CustomFieldAnswerViewModel vm = formAnswerApiService.findViewModelForField(response.getAnswers(), field);
        if (vm == null) {
            return;
        }
        Object rawValue = FieldAnswerMaps.unwrap(value);
        Object typed;
        try {
            typed = coerceAnswerValue(field, rawValue);
        } catch (ResponseStatusException rex) {
            throw rex;
        } catch (RuntimeException ex) {
            log.warn("Valeur ignorée pour champ id={} ({}): {}", fieldId, field.getClass().getSimpleName(), ex.toString());
            return;
        }
        if (typed == null && rawValue != null) {
            log.debug("Valeur non convertible pour champ id={} type {}", fieldId, field.getClass().getSimpleName());
            return;
        }
        formService.applyTypedValueToAnswer(vm, typed);
    }

    private Object coerceAnswerValue(CustomField field, Object raw) {
        if (raw == null) return null;
        if (field instanceof CustomFieldInteger) return coerceInteger(raw);
        if (field instanceof CustomFieldText) return String.valueOf(raw);
        if (field instanceof CustomFieldDateTime) return coerceDateTime(raw);
        if (field instanceof CustomFieldSelectOneFromFieldCode) return coerceConcept(raw);
        if (field instanceof CustomFieldSelectOnePerson) return coercePerson(raw);
        if (field instanceof CustomFieldSelectOneActionUnit) return coerceActionUnit(raw);
        if (field instanceof CustomFieldSelectOneSpatialUnit) return coerceSpatialUnit(raw);
        log.debug("Type de champ non pris en charge pour l'API v1: {}", field.getClass().getSimpleName());
        return null;
    }

    private Object coerceInteger(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    private Object coerceDateTime(Object raw) {
        if (raw instanceof OffsetDateTime odt) {
            return odt;
        }
        if (raw instanceof String s) {
            return OffsetDateTime.parse(s);
        }
        throw new IllegalArgumentException("Format datetime attendu (chaîne ISO-8601 ou OffsetDateTime)");
    }

    private Object coerceConcept(Object raw) {
        long conceptId = requireLongId(raw, "concept");
        Concept c = conceptRepository.findById(conceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concept introuvable: " + conceptId));
        return conceptMapper.convert(c);
    }

    private Object coercePerson(Object raw) {
        long personId = requireLongId(raw, "personne");
        Person p = personService.findById(personId);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personne introuvable: " + personId);
        }
        return personMapper.convert(p);
    }

    private Object coerceActionUnit(Object raw) {
        long actionId = requireLongId(raw, "unité d'action");
        ActionUnitDTO au = actionUnitService.findById(actionId);
        if (au == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet introuvable: " + actionId);
        }
        return actionUnitSummaryFromFull(au);
    }

    private Object coerceSpatialUnit(Object raw) {
        long suId = requireLongId(raw, "unité spatiale");
        try {
            return new SpatialUnitSummaryDTO(spatialUnitService.findById(suId));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité spatiale introuvable: " + suId);
        }
    }

    private static ActionUnitSummaryDTO actionUnitSummaryFromFull(ActionUnitDTO au) {
        ActionUnitSummaryDTO s = new ActionUnitSummaryDTO(au);
        s.setId(au.getId());
        s.setName(au.getName());
        s.setFullIdentifier(au.getFullIdentifier());
        s.setIdentifier(au.getIdentifier());
        s.setType(au.getType());
        s.setBeginDate(au.getBeginDate());
        s.setEndDate(au.getEndDate());
        return s;
    }

    private static long requireLongId(Object raw, String label) {
        Long id = extractLongId(raw);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu pour " + label);
        }
        return id;
    }

    private static Long extractLongId(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) {
                return n.longValue();
            }
            if (id instanceof String s && !s.isBlank()) {
                return Long.parseLong(s);
            }
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Long.parseLong(s);
        }
        return null;
    }

}
