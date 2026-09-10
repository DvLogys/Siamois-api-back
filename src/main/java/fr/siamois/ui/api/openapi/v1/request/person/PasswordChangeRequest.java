package fr.siamois.ui.api.openapi.v1.request.person;

public record PasswordChangeRequest(String currentPassword, String newPassword) {}
