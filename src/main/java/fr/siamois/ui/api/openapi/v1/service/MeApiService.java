package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.InvalidEmailException;
import fr.siamois.domain.models.exceptions.auth.InvalidNameException;
import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import fr.siamois.domain.models.exceptions.auth.InvalidUsernameException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.request.person.PasswordChangeRequest;
import fr.siamois.ui.api.openapi.v1.request.person.ProfilePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.person.ProfileResource;
import fr.siamois.utils.AuthenticatedUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class MeApiService {

    private final PersonService personService;
    private final PersonMapper personMapper;
    private final I18nApiService i18nApiService;

    @Transactional(readOnly = true)
    public ProfileResource currentProfile() {
        Person person = requireAuthenticatedPerson();
        return toResource(person, currentLangCode(person));
    }

    @Transactional
    public ProfileResource updateCurrentProfile(ProfilePatchRequest request) {
        Person person = requireAuthenticatedPerson();
        if (request == null) {
            return toResource(person, currentLangCode(person));
        }

        PersonDTO profile = personMapper.convert(person);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Profil illisible");
        }

        // Même règle que ProfileSettingsBean.saveProfile : une valeur vide ne remplace pas la valeur enregistrée.
        applyIfFilled(request.name(), profile::setName);
        applyIfFilled(request.lastname(), profile::setLastname);
        applyIfFilled(request.email(), profile::setEmail);
        applyIfFilled(request.username(), profile::setUsername);

        try {
            personService.updatePerson(profile);
        } catch (UserAlreadyExistException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (InvalidNameException | InvalidUsernameException
                 | InvalidEmailException | InvalidPasswordException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        String langCode = request.langCode() != null
                ? saveLangPreference(profile, request.langCode())
                : currentLangCode(person);

        return toResource(personService.findById(person.getId()), langCode);
    }

    @Transactional
    public void changeCurrentPassword(PasswordChangeRequest request) {
        Person person = requireAuthenticatedPerson();
        if (request == null || isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mot de passe actuel et nouveau mot de passe requis");
        }
        if (!personService.passwordMatch(person, request.currentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect");
        }
        try {
            personService.updatePassword(person.getId(), request.newPassword());
        } catch (InvalidPasswordException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private String saveLangPreference(PersonDTO profile, String langCode) {
        if (!i18nApiService.isSupportedLanguage(langCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Langue non disponible : " + langCode);
        }
        String normalized = langCode.trim();
        PersonSettings settings = personService.createOrGetSettingsOf(profile);
        settings.setLangCode(normalized);
        personService.updatePersonSettings(settings);
        return normalized;
    }

    private String currentLangCode(Person person) {
        PersonDTO profile = personMapper.convert(person);
        if (profile == null) {
            return null;
        }
        return personService.createOrGetSettingsOf(profile).getLangCode();
    }

    private ProfileResource toResource(Person person, String langCode) {
        return new ProfileResource(
                person.getId(),
                person.getUsername(),
                person.getName(),
                person.getLastname(),
                person.getEmail(),
                person.isPassToModify(),
                langCode);
    }

    private Person requireAuthenticatedPerson() {
        return AuthenticatedUserUtils.getAuthenticatedUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void applyIfFilled(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
