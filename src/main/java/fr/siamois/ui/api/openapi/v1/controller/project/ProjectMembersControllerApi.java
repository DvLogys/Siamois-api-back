package fr.siamois.ui.api.openapi.v1.controller.project;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{id}")
@RequiredArgsConstructor
public class ProjectMembersControllerApi {

    private final ProjectApiService projectApiService;
    private final MemberApiService memberApiService;

    @GetMapping("/members")
    public ResponseEntity<ListResponse<MemberResource>> listMembers(@PathVariable String id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<MemberResource> members = memberApiService.listProjectMembers(caller, id);
        return ResponseEntity.ok(new ListResponse<>(members, listMeta(members.size())));
    }

    @GetMapping("/profiles")
    public ResponseEntity<ListResponse<ProfileSummaryResource>> listProfiles(@PathVariable String id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ProfileSummaryResource> profiles = memberApiService.listProjectProfiles(caller, id);
        return ResponseEntity.ok(new ListResponse<>(profiles, listMeta(profiles.size())));
    }

    @PostMapping(value = "/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Response<MemberResource>> addMember(
            @PathVariable String id,
            @RequestBody MemberCreateRequest request) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(memberApiService.addProjectMember(caller, id, request)));
    }

    @DeleteMapping("/members/{personId}")
    public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable Long personId) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        memberApiService.removeProjectMember(caller, id, personId);
        return ResponseEntity.noContent().build();
    }

    private ListMeta listMeta(int size) {
        return new ListMeta((long) size, size, 0L);
    }
}
