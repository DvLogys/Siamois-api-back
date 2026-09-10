package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.request.person.PasswordChangeRequest;
import fr.siamois.ui.api.openapi.v1.request.person.ProfilePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.person.ProfileResource;
import fr.siamois.ui.api.openapi.v1.service.MeApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeControllerApi {

    private final MeApiService meApiService;

    @GetMapping
    public ResponseEntity<Response<ProfileResource>> getCurrentProfile() {
        return ResponseEntity.ok(new Response<>(meApiService.currentProfile()));
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<ProfileResource>> patchCurrentProfile(
            @RequestBody ProfilePatchRequest request) {
        return ResponseEntity.ok(new Response<>(meApiService.updateCurrentProfile(request)));
    }

    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changeCurrentPassword(@RequestBody PasswordChangeRequest request) {
        meApiService.changeCurrentPassword(request);
        return ResponseEntity.noContent().build();
    }
}
