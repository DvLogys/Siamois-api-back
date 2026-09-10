package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.LangService;
import fr.siamois.ui.api.openapi.v1.resource.i18n.LanguagesResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class I18nApiServiceTest {

    @Mock
    private LangService langService;

    private I18nApiService i18nApiService;

    @BeforeEach
    void setUp() {
        i18nApiService = new I18nApiService(langService);
        when(langService.getDefaultLang()).thenReturn("fr");
        when(langService.getAvailableLanguages()).thenReturn(new String[]{"en", " fr "});
    }

    @Test
    void availableLanguages_trimsDeclaredCodesAndKeepsDefault() {
        LanguagesResource resource = i18nApiService.availableLanguages();

        assertThat(resource.available()).containsExactly("en", "fr");
        assertThat(resource.defaultLang()).isEqualTo("fr");
    }

    @Test
    void isSupportedLanguage_acceptsDeclaredCodesOnly() {
        assertThat(i18nApiService.isSupportedLanguage("fr")).isTrue();
        assertThat(i18nApiService.isSupportedLanguage("EN")).isTrue();
        assertThat(i18nApiService.isSupportedLanguage("de")).isFalse();
        assertThat(i18nApiService.isSupportedLanguage(null)).isFalse();
    }

    @Test
    void messagesFor_loadsRequestedBundle() {
        Map<String, String> messages = i18nApiService.messagesFor("fr");

        assertThat(messages).isNotEmpty();
        assertThat(messages).containsKey("spatialunit.label");
    }

    @Test
    void messagesFor_overlaysTranslationOnBaseBundle() {
        Map<String, String> english = i18nApiService.messagesFor("en");
        Map<String, String> french = i18nApiService.messagesFor("fr");

        // Le bundle de base fournit les clés, la surcharge de langue fournit la traduction.
        assertThat(french.keySet()).containsAll(english.keySet());
        assertThat(french.get("spatialunit.label")).isNotEqualTo(english.get("spatialunit.label"));
    }

    @Test
    void messagesFor_fallsBackToDefaultWhenLanguageIsUnknownOrMissing() {
        Map<String, String> expected = i18nApiService.messagesFor("fr");

        assertThat(i18nApiService.messagesFor(null)).isEqualTo(expected);
        assertThat(i18nApiService.messagesFor("  ")).isEqualTo(expected);
        assertThat(i18nApiService.messagesFor("de")).isEqualTo(expected);
    }

    @Test
    void messagesFor_rejectsPathTraversalInLanguageCode() {
        Map<String, String> expected = i18nApiService.messagesFor("fr");

        assertThat(i18nApiService.messagesFor("../application")).isEqualTo(expected);
        assertThat(i18nApiService.messagesFor("fr/../../application")).isEqualTo(expected);
    }
}
