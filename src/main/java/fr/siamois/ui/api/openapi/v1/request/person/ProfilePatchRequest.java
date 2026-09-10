package fr.siamois.ui.api.openapi.v1.request.person;

public record ProfilePatchRequest(
        String name,
        String lastname,
        String email,
        String username,
        String langCode
) {}
