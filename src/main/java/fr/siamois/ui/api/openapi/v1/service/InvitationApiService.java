package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.InvalidEmailException;
import fr.siamois.domain.models.exceptions.auth.InvalidNameException;
import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import fr.siamois.domain.models.exceptions.auth.InvalidUsernameException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.request.auth.InvitationRegisterRequest;
import fr.siamois.ui.api.openapi.v1.resource.auth.InvitationResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class InvitationApiService {

    private final PendingPersonService pendingPersonService;
    private final PersonService personService;
    private final PersonMapper personMapper;

    @Transactional(readOnly = true)
    public InvitationResource verifyInvitation(String token) {
        PendingPerson pending = requireValidInvitation(token);
        Person invited = pending.getDisabledPerson();
        return new InvitationResource(
                pending.getRegisterToken(),
                invited.getEmail(),
                invited.getName(),
                invited.getLastname(),
                invited.getUsername(),
                pending.getPendingInvitationExpirationDate());
    }

    @Transactional
    public void completeInvitation(String token, InvitationRegisterRequest request) {
        PendingPerson pending = requireValidInvitation(token);
        if (request == null || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe requis");
        }

        PersonDTO invited = personMapper.convert(pending.getDisabledPerson());
        if (invited == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invitation illisible");
        }

        applyIfFilled(request.email(), invited::setEmail);
        applyIfFilled(request.name(), invited::setName);
        applyIfFilled(request.lastname(), invited::setLastname);
        applyIfFilled(request.username(), invited::setUsername);

        try {
            // Active le compte et consomme l'invitation dans la même transaction.
            personService.registerInvitedPerson(invited, request.password());
        } catch (UserAlreadyExistException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (InvalidNameException | InvalidUsernameException
                 | InvalidEmailException | InvalidPasswordException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private PendingPerson requireValidInvitation(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation introuvable");
        }
        PendingPerson pending = pendingPersonService.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation introuvable"));
        if (pendingPersonService.isExpired(pending)) {
            // L'invitation reste en base : un gestionnaire peut la renouveler depuis la page des membres.
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation expirée");
        }
        return pending;
    }

    private void applyIfFilled(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }
}
