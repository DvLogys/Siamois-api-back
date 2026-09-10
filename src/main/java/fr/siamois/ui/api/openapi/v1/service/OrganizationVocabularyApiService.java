package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.request.vocabulary.ThesaurusConfigRequest;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationVocabularyApiService {

    private static final String VOCABULARY_RESOURCE_TYPE = "vocabularies";

    private final ProjectApiService projectApiService;
    private final SettingsAccessApiService settingsAccessApiService;
    private final VocabularyOpenApiService vocabularyOpenApiService;
    private final VocabularyService vocabularyService;
    private final FieldConfigurationService fieldConfigurationService;

    @Transactional(readOnly = true)
    public List<ResolvedConceptResource> listConcepts(ProjectApiCaller caller, Long organizationId,
                                                      String fieldCode, String query, int limit, int offset,
                                                      String lang) {
        if (fieldCode == null || fieldCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le paramètre fieldCode est obligatoire");
        }
        projectApiService.requireOrganization(organizationId, caller);

        List<ConceptAutocompleteDTO> all = vocabularyOpenApiService.getConceptsForOrganization(
                organizationId, fieldCode, query, lang, caller.person());

        // Comme au niveau projet : `q` bascule en mode suggestion, non paginé.
        List<ConceptAutocompleteDTO> page = query != null ? all : paginate(all, offset, limit);
        return page.stream().map(ResolvedConceptResource::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> listFieldCodes(ProjectApiCaller caller, Long organizationId, String lang) {
        projectApiService.requireOrganization(organizationId, caller);
        return vocabularyOpenApiService.getAvailableFieldCodesForOrganization(organizationId, lang, caller.person());
    }

    /**
     * Reprend {@code InstitutionThesaurusSettingsBean.saveConfig} : l'import du thésaurus et la
     * configuration des champs sont synchrones, l'appel ne rend la main qu'une fois terminé.
     */
    @Transactional
    public VocabularyResource configureThesaurus(ProjectApiCaller caller, Long organizationId,
                                                 ThesaurusConfigRequest request) {
        InstitutionDTO organization = settingsAccessApiService.requireManageableOrganization(caller, organizationId);
        if (request == null || request.thesaurusUrl() == null || request.thesaurusUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "thesaurusUrl requis");
        }

        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(request.thesaurusUrl().trim());
            fieldConfigurationService.setupFieldConfigurationForInstitution(
                    new UserInfo(organization, caller.person(), null), vocabulary);
            return toResource(vocabulary);
        } catch (InvalidEndpointException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URI de thésaurus invalide");
        } catch (NotSiamoisThesaurusException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce thésaurus n'est pas un thésaurus Siamois");
        } catch (ErrorProcessingExpansionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur lors du traitement du thésaurus");
        }
    }

    private VocabularyResource toResource(Vocabulary vocabulary) {
        VocabularyResource resource = new VocabularyResource();
        resource.setResourceType(VOCABULARY_RESOURCE_TYPE);
        resource.setId(vocabulary.getId() == null ? null : String.valueOf(vocabulary.getId()));
        resource.setExternalId(vocabulary.getExternalVocabularyId());
        resource.setBaseUri(vocabulary.getBaseUri());
        resource.setUri(vocabulary.getUri());
        return resource;
    }

    private static <T> List<T> paginate(List<T> list, int offset, int limit) {
        if (offset >= list.size()) {
            return List.of();
        }
        return list.subList(offset, Math.min(offset + limit, list.size()));
    }
}
