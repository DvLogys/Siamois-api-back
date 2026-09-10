package fr.siamois.ui.api.openapi.v1.resource.person;

public record ProfileResource(
        Long id,
        String username,
        String name,
        String lastname,
        String email,
        boolean passwordToModify,
        String langCode
) {}
