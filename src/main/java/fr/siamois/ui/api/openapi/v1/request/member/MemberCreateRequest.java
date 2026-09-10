package fr.siamois.ui.api.openapi.v1.request.member;

import java.util.List;

public record MemberCreateRequest(Long personId, List<Long> profileIds) {}
