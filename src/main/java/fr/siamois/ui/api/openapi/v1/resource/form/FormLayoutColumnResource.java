package fr.siamois.ui.api.openapi.v1.resource.form;

public record FormLayoutColumnResource(
        String fieldId,
        String className,
        boolean required,
        boolean readOnly
) {}
