package fr.siamois.ui.api.openapi.v1.request.auth;

public record InvitationRegisterRequest(
        String email,
        String name,
        String lastname,
        String username,
        String password
) {}
