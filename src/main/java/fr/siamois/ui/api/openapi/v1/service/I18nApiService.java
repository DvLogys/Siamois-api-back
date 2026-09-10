package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.LangService;
import fr.siamois.ui.api.openapi.v1.resource.i18n.LanguagesResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class I18nApiService {

    private static final String BUNDLE_BASE = "language/messages";

    private final LangService langService;

    public LanguagesResource availableLanguages() {
        return new LanguagesResource(supportedLanguages(), langService.getDefaultLang());
    }

    public Map<String, String> messagesFor(String requestedLang) {
        String lang = resolveSupportedLang(requestedLang);

        Properties merged = new Properties();
        readBundleInto(merged, BUNDLE_BASE + ".properties");
        readBundleInto(merged, BUNDLE_BASE + "_" + lang + ".properties");

        Map<String, String> messages = new TreeMap<>();
        merged.forEach((key, value) -> messages.put(String.valueOf(key), String.valueOf(value)));
        return messages;
    }

    public boolean isSupportedLanguage(String lang) {
        return lang != null && supportedLanguages().stream().anyMatch(lang.trim()::equalsIgnoreCase);
    }

    public List<String> supportedLanguages() {
        String[] declared = langService.getAvailableLanguages();
        if (declared == null) {
            return List.of(langService.getDefaultLang());
        }
        return Arrays.stream(declared)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }

    // Le code de langue entre dans un nom de fichier : le restreindre aux langues déclarées
    // (siamois.lang.available) est ce qui empêche un "../.." de sortir du dossier des bundles.
    private String resolveSupportedLang(String requestedLang) {
        if (!isSupportedLanguage(requestedLang)) {
            return langService.getDefaultLang();
        }
        return requestedLang.trim();
    }

    private void readBundleInto(Properties target, String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            target.load(reader);
        } catch (IOException e) {
            log.warn("Bundle de messages illisible : {}", classpathLocation, e);
        }
    }
}
