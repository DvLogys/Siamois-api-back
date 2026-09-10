package fr.siamois.ui.api.openapi.v1.resource.auth;

import java.time.OffsetDateTime;

public record InvitationResource(
        String token,
        String email,
        String name,
        String lastname,
        String username,
        OffsetDateTime expiresAt
) {}
