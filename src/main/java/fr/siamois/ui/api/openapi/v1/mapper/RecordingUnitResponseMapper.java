package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceCounts;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public abstract class RecordingUnitResponseMapper implements Converter<RecordingUnitDTO, RecordingUnitResource> {

    @Autowired
    protected LabelService labelService;

    @Mapping(target = "resourceType", constant = "recording-units")
    @Mapping(target = "id", expression = "java(String.valueOf(dto.getId()))")
    @Mapping(target = "projectId", expression = "java(dto.getActionUnit() != null ? String.valueOf(dto.getActionUnit().getId()) : null)")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "geom", ignore = true)
    @Mapping(target = "answers", expression = "java(java.util.Map.of())")
    @Mapping(target = "count", expression = "java(toResourceCounts(dto))")
    @Mapping(target = "actionUnit", expression = "java(toRef(dto.getActionUnit()))")
    @Mapping(target = "spatialUnit", expression = "java(toRef(dto.getSpatialUnit()))")
    @Mapping(target = "author", expression = "java(toRef(dto.getAuthor()))")
    @Mapping(target = "canEdit", ignore = true)
    @Mapping(target = "links", expression = "java(fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceLinks.of(dto.getFullIdentifier()))")
    public abstract RecordingUnitResource convert(RecordingUnitDTO dto);

    @Mapping(target = "resourceType", constant = "recording-units")
    @Mapping(target = "id", expression = "java(String.valueOf(dto.getId()))")
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "syncRevision", ignore = true)
    @Mapping(target = "type", source = "type")
    @Mapping(target = "geom", ignore = true)
    @Mapping(target = "answers", expression = "java(java.util.Map.of())")
    @Mapping(target = "count", expression = "java(new fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceCounts(null, null, null, null, null))")
    @Mapping(target = "actionUnit", ignore = true)
    @Mapping(target = "spatialUnit", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "matrixColor", ignore = true)
    @Mapping(target = "openingDate", ignore = true)
    @Mapping(target = "closingDate", ignore = true)
    @Mapping(target = "tpq", ignore = true)
    @Mapping(target = "taq", ignore = true)
    @Mapping(target = "canEdit", ignore = true)
    @Mapping(target = "links", expression = "java(fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceLinks.of(dto.getFullIdentifier()))")
    public abstract RecordingUnitResource toResource(RecordingUnitSummaryDTO dto);

    ResolvedConceptResource toResolvedConcept(ConceptDTO concept) {
        if (concept == null) return null;
        ResolvedConceptResource r = new ResolvedConceptResource();
        r.setResourceType("concepts");
        r.setId(String.valueOf(concept.getId()));
        r.setExternalUrl(concept.getExternalId());
        r.setResolvedLabel(labelService.findLabelOf(concept, "fr").getLabel());
        return r;
    }

    /**
     * Une entité liée réduite à ce qu'une cellule de tableau en montre : son identifiant, son type
     * et son libellé. Le libellé est déjà chargé sur le résumé, aucune résolution n'est nécessaire.
     */
    protected ResourceRef toRef(ActionUnitSummaryDTO actionUnit) {
        return actionUnit == null ? null
                : new ResourceRef(String.valueOf(actionUnit.getId()), "action-units", actionUnit.getName());
    }

    protected ResourceRef toRef(SpatialUnitSummaryDTO spatialUnit) {
        return spatialUnit == null ? null
                : new ResourceRef(String.valueOf(spatialUnit.getId()), "spatial-units", spatialUnit.getName());
    }

    protected ResourceRef toRef(PersonDTO person) {
        return person == null ? null
                : new ResourceRef(String.valueOf(person.getId()), "persons", person.displayName());
    }

    protected RecordingUnitResourceCounts toResourceCounts(RecordingUnitDTO dto) {
        return new RecordingUnitResourceCounts(
                dto.getChildrenCount() != null ? (long) dto.getChildrenCount() : null,
                dto.getSpecimenCount(),
                dto.getParentsCount() != null ? (long) dto.getParentsCount() : null,
                null,
                dto.getRelationshipCount()
        );
    }
}
