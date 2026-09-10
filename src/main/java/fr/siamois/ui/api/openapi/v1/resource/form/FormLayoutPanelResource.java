package fr.siamois.ui.api.openapi.v1.resource.form;

import java.util.List;

public record FormLayoutPanelResource(
        String name,
        String className,
        List<FormLayoutRowResource> rows
) {}
