package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.dto.entity.BookmarkDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.request.bookmark.BookmarkCreateRequest;
import fr.siamois.ui.api.openapi.v1.resource.bookmark.BookmarkResource;
import fr.siamois.ui.api.openapi.v1.resource.bookmark.BookmarkStatusResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkApiService {

    private final ProjectApiService projectApiService;
    private final BookmarkService bookmarkService;

    @Transactional(readOnly = true)
    public Page<BookmarkDTO> pageBookmarks(ProjectApiCaller caller, Long organizationId, int offset, int limit) {
        projectApiService.validatePagedListRequest(offset, limit);
        UserInfo userInfo = userInfoFor(caller, organizationId);
        Pageable pageable = PageRequest.of(offset / limit, limit);
        return bookmarkService.findAll(userInfo, pageable);
    }

    @Transactional
    public BookmarkResource addBookmark(ProjectApiCaller caller, BookmarkCreateRequest request) {
        if (request == null || request.organizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId requis");
        }
        String resourceUri = requireResourceUri(request.resourceUri());
        UserInfo userInfo = userInfoFor(caller, request.organizationId());

        String title = request.title() == null || request.title().isBlank()
                ? resourceUri
                : request.title().trim();

        // Le favori est unique par (personne, organisation, URI) : reposer deux fois le même
        // ne doit pas créer de doublon.
        if (Boolean.TRUE.equals(bookmarkService.isRessourceBookmarkedByUser(userInfo, resourceUri))) {
            bookmarkService.deleteBookmark(userInfo, resourceUri);
        }
        var saved = bookmarkService.save(userInfo, resourceUri, title);
        return new BookmarkResource(saved.getId(), saved.getTitleCode(), saved.getResourceUri(),
                typeOf(saved.getResourceUri()));
    }

    @Transactional(readOnly = true)
    public BookmarkStatusResource bookmarkStatus(ProjectApiCaller caller, Long organizationId, String resourceUri) {
        String uri = requireResourceUri(resourceUri);
        UserInfo userInfo = userInfoFor(caller, organizationId);
        boolean bookmarked = Boolean.TRUE.equals(bookmarkService.isRessourceBookmarkedByUser(userInfo, uri));
        return new BookmarkStatusResource(uri, bookmarked);
    }

    @Transactional
    public void removeBookmark(ProjectApiCaller caller, Long organizationId, String resourceUri) {
        String uri = requireResourceUri(resourceUri);
        UserInfo userInfo = userInfoFor(caller, organizationId);
        bookmarkService.deleteBookmark(userInfo, uri);
    }

    public List<BookmarkResource> toResources(List<BookmarkDTO> bookmarks) {
        return bookmarks.stream()
                .map(dto -> new BookmarkResource(dto.getId(), dto.getTitle(), dto.getResourceUri(),
                        typeOf(dto.getResourceUri())))
                .toList();
    }

    private UserInfo userInfoFor(ProjectApiCaller caller, Long organizationId) {
        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId requis");
        }
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        InstitutionDTO organization = projectApiService.requireOrganization(organizationId, caller);
        return new UserInfo(organization, caller.person(), null);
    }

    private String requireResourceUri(String resourceUri) {
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceUri requis");
        }
        return resourceUri.trim();
    }

    private String typeOf(String resourceUri) {
        return PanelResourceTypes.fromResourceUri(resourceUri)
                .map(PanelResourceTypes::slug)
                .orElse(null);
    }
}
