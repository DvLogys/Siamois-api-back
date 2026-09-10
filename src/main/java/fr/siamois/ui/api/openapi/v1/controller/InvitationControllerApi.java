package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.request.auth.InvitationRegisterRequest;
import fr.siamois.ui.api.openapi.v1.resource.auth.InvitationResource;
import fr.siamois.ui.api.openapi.v1.service.InvitationApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/invitations")
@RequiredArgsConstructor
public class InvitationControllerApi {

    private final InvitationApiService invitationApiService;

    @GetMapping("/{token}")
    public ResponseEntity<Response<InvitationResource>> verifyInvitation(@PathVariable String token) {
        return ResponseEntity.ok(new Response<>(invitationApiService.verifyInvitation(token)));
    }

    @PostMapping(value = "/{token}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> completeInvitation(
            @PathVariable String token,
            @RequestBody InvitationRegisterRequest request) {
        invitationApiService.completeInvitation(token, request);
        return ResponseEntity.noContent().build();
    }
}
