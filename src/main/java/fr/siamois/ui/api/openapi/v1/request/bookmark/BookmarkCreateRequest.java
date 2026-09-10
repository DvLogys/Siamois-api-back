package fr.siamois.ui.api.openapi.v1.request.bookmark;

public record BookmarkCreateRequest(Long organizationId, String resourceUri, String title) {}
