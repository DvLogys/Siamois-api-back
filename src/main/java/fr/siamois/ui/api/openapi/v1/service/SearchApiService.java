package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SearchResultDTO;
import fr.siamois.infrastructure.database.repositories.misc.SearchRepository;
import fr.siamois.ui.api.openapi.v1.resource.search.SearchResultItemResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchApiService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final ProjectApiService projectApiService;
    private final SearchRepository searchRepository;

    @Transactional(readOnly = true)
    public List<SearchResultItemResource> searchInOrganization(ProjectApiCaller caller, Long organizationId,
                                                               String query, Integer limit) {
        InstitutionDTO organization = projectApiService.requireOrganization(organizationId, caller);

        List<SearchResultDTO> rows = searchRepository.findResultsFor(query, organization, caller.person());

        List<SearchResultItemResource> results = new ArrayList<>();
        for (SearchResultDTO row : rows) {
            toResource(row).ifPresent(results::add);
        }
        return results.size() > effectiveLimit(limit) ? results.subList(0, effectiveLimit(limit)) : results;
    }

    private int effectiveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    // basic_search renvoie une ligne par correspondance avec un seul identifiant renseigné :
    // c'est lui qui détermine le type, comme SearchBean.onResultSelect côté JSF.
    private java.util.Optional<SearchResultItemResource> toResource(SearchResultDTO row) {
        if (row == null) {
            return java.util.Optional.empty();
        }
        if (row.getRecordingUnitId() != null) {
            return java.util.Optional.of(item(PanelResourceTypes.RECORDING_UNIT, row.getRecordingUnitId(), row));
        }
        if (row.getSpatialUnitId() != null) {
            return java.util.Optional.of(item(PanelResourceTypes.SPATIAL_UNIT, row.getSpatialUnitId(), row));
        }
        if (row.getActionUnitId() != null) {
            return java.util.Optional.of(item(PanelResourceTypes.ACTION_UNIT, row.getActionUnitId(), row));
        }
        if (row.getSpecimenId() != null) {
            return java.util.Optional.of(item(PanelResourceTypes.SPECIMEN, row.getSpecimenId(), row));
        }
        return java.util.Optional.empty();
    }

    private SearchResultItemResource item(PanelResourceTypes type, Long id, SearchResultDTO row) {
        return new SearchResultItemResource(type.slug(), id, row.getMatchingTerm());
    }
}
