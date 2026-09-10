package fr.siamois.ui.api.openapi.v1.resource.member;

import java.util.List;

public record MemberResource(
        Long id,
        Long personId,
        String username,
        String name,
        String lastname,
        String email,
        List<ProfileSummaryResource> profiles,
        String accountStatus
) {}
