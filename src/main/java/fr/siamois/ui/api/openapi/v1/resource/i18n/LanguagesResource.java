package fr.siamois.ui.api.openapi.v1.resource.i18n;

import java.util.List;

public record LanguagesResource(List<String> available, String defaultLang) {}
