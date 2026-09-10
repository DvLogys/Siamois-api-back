package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.request.member.MemberCreateRequest;
import fr.siamois.ui.api.openapi.v1.resource.member.MemberResource;
import fr.siamois.ui.api.openapi.v1.resource.member.ProfileSummaryResource;
import fr.siamois.ui.api.openapi.v1.service.MemberApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{id}")
@RequiredArgsConstructor
public class OrganizationMembersControllerApi {

    private final ProjectApiService projectApiService;
    private final MemberApiService memberApiService;

    @GetMapping("/members")
    public ResponseEntity<ListResponse<MemberResource>> listMembers(@PathVariable Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<MemberResource> members = memberApiService.listOrganizationMembers(caller, id);
        return ResponseEntity.ok(new ListResponse<>(members, listMeta(members.size())));
    }

    @GetMapping("/profiles")
    public ResponseEntity<ListResponse<ProfileSummaryResource>> listProfiles(@PathVariable Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ProfileSummaryResource> profiles = memberApiService.listOrganizationProfiles(caller, id);
        return ResponseEntity.ok(new ListResponse<>(profiles, listMeta(profiles.size())));
    }

    @PostMapping(value = "/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<MemberResource>> addMember(
            @PathVariable Long id,
            @RequestBody MemberCreateRequest request) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(memberApiService.addOrganizationMember(caller, id, request)));
    }

    @DeleteMapping("/members/{personId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long personId) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        memberApiService.removeOrganizationMember(caller, id, personId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{personId}/invitation")
    public ResponseEntity<Void> resendInvitation(
            @PathVariable Long id,
            @PathVariable Long personId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        memberApiService.resendOrganizationInvitation(caller, id, personId, acceptLanguage);
        return ResponseEntity.noContent().build();
    }

    private ListMeta listMeta(int size) {
        return new ListMeta((long) size, size, 0L);
    }
}
