package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.BookmarkDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.request.bookmark.BookmarkCreateRequest;
import fr.siamois.ui.api.openapi.v1.resource.bookmark.BookmarkResource;
import fr.siamois.ui.api.openapi.v1.resource.bookmark.BookmarkStatusResource;
import fr.siamois.ui.api.openapi.v1.service.BookmarkApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarksControllerApi {

    private final ProjectApiService projectApiService;
    private final BookmarkApiService bookmarkApiService;

    @GetMapping
    public ResponseEntity<ListResponse<BookmarkResource>> listBookmarks(
            @RequestParam Long organizationId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<BookmarkDTO> page = bookmarkApiService.pageBookmarks(caller, organizationId, offset, limit);

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return ResponseEntity.ok(new ListResponse<>(bookmarkApiService.toResources(page.getContent()), meta));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<BookmarkResource>> addBookmark(@RequestBody BookmarkCreateRequest request) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(bookmarkApiService.addBookmark(caller, request)));
    }

    // Le SPA lit cet état sans enveloppe (bookmarksApi.status renvoie directement l'objet).
    @GetMapping("/status")
    public ResponseEntity<BookmarkStatusResource> getBookmarkStatus(
            @RequestParam Long organizationId,
            @RequestParam String resourceUri) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(bookmarkApiService.bookmarkStatus(caller, organizationId, resourceUri));
    }

    @DeleteMapping
    public ResponseEntity<Void> removeBookmark(
            @RequestParam Long organizationId,
            @RequestParam String resourceUri) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        bookmarkApiService.removeBookmark(caller, organizationId, resourceUri);
        return ResponseEntity.noContent().build();
    }
}
