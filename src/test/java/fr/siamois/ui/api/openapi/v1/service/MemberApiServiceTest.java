package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.OrganizationMembersServiceInterface;
import fr.siamois.domain.services.ProjectMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.request.member.MemberCreateRequest;
import fr.siamois.ui.api.openapi.v1.resource.member.MemberResource;
import fr.siamois.ui.email.InvitationMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberApiServiceTest {

    private static final long ORG_ID = 10L;
    private static final long PERSON_ID = 5L;

    @Mock
    private SettingsAccessApiService settingsAccessApiService;
    @Mock
    private OrganizationMembersServiceInterface organizationMembersService;
    @Mock
    private ProjectMembersServiceInterface projectMembersService;
    @Mock
    private PendingPersonService pendingPersonService;
    @Mock
    private PersonService personService;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private InvitationMailer invitationMailer;
    @Mock
    private LangService langService;

    private MemberApiService memberApiService;
    private ProjectApiCaller caller;
    private InstitutionDTO organization;
    private InstitutionMemberDTO member;

    @BeforeEach
    void setUp() {
        memberApiService = new MemberApiService(settingsAccessApiService, organizationMembersService,
                projectMembersService, pendingPersonService, personService, personMapper,
                invitationMailer, langService);

        organization = new InstitutionDTO();
        organization.setId(ORG_ID);
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(ORG_ID), List.of(organization));

        PersonDTO person = new PersonDTO();
        person.setId(PERSON_ID);
        person.setUsername("ada");
        member = new InstitutionMemberDTO();
        member.setPerson(person);
        member.setProfiles(List.of());

        when(settingsAccessApiService.requireManageableOrganization(any(), any())).thenReturn(organization);
        when(organizationMembersService.findMembersOf(organization)).thenReturn(List.of(member));
        when(pendingPersonService.findPersonIdsWithPendingInvitation(anyCollection())).thenReturn(Set.of());
        when(pendingPersonService.findPersonIdsWithExpiredInvitation(anyCollection())).thenReturn(Set.of());
    }

    /**
     * Le service domaine renvoie false plutôt que de lever : sans traduction explicite en conflit,
     * l'API répondrait 204 en laissant croire que le retrait a eu lieu.
     */
    @Test
    void removeOrganizationMember_reportsConflictWhenDomainRefusesLastManagerRemoval() {
        when(organizationMembersService.removeMemberFromInstitution(organization, member)).thenReturn(false);

        assertThatThrownBy(() -> memberApiService.removeOrganizationMember(caller, ORG_ID, PERSON_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
    }

    @Test
    void removeOrganizationMember_succeedsWhenDomainAllowsIt() {
        when(organizationMembersService.removeMemberFromInstitution(organization, member)).thenReturn(true);

        memberApiService.removeOrganizationMember(caller, ORG_ID, PERSON_ID);

        verify(organizationMembersService).removeMemberFromInstitution(organization, member);
    }

    @Test
    void removeOrganizationMember_returnsNotFoundForSomeoneWhoIsNotAMember() {
        assertThatThrownBy(() -> memberApiService.removeOrganizationMember(caller, ORG_ID, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(organizationMembersService, never()).removeMemberFromInstitution(any(), any());
    }

    /** Un profil hors de la portée visée ne doit pas pouvoir être attribué par son seul identifiant. */
    @Test
    void addOrganizationMember_refusesProfileThatDoesNotBelongToTheOrganization() {
        ProfileDTO available = new ProfileDTO();
        available.setId(1L);
        when(organizationMembersService.findAvailableProfiles(organization)).thenReturn(List.of(available));
        when(personService.findById(PERSON_ID)).thenReturn(new fr.siamois.domain.models.auth.Person());
        when(personMapper.convert(any())).thenReturn(member.getPerson());

        MemberCreateRequest request = new MemberCreateRequest(PERSON_ID, List.of(1L, 42L));

        assertThatThrownBy(() -> memberApiService.addOrganizationMember(caller, ORG_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(organizationMembersService, never()).addMemberToInstitution(any(), any(), any());
    }

    @Test
    void listOrganizationMembers_marksDisabledAccountWithPendingInvitationAsInvited() {
        member.getPerson().setEnabled(false);
        when(pendingPersonService.findPersonIdsWithPendingInvitation(anyCollection())).thenReturn(Set.of(PERSON_ID));

        List<MemberResource> members = memberApiService.listOrganizationMembers(caller, ORG_ID);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).accountStatus()).isEqualTo("INVITED");
        assertThat(members.get(0).personId()).isEqualTo(PERSON_ID);
    }

    @Test
    void listOrganizationMembers_marksEnabledAccountAsActive() {
        member.getPerson().setEnabled(true);

        assertThat(memberApiService.listOrganizationMembers(caller, ORG_ID).get(0).accountStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    void removeProjectMember_reportsConflictWhenDomainRefusesLastManagerRemoval() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(77L);
        ProjectMemberDTO projectMember = new ProjectMemberDTO();
        projectMember.setPerson(member.getPerson());
        when(settingsAccessApiService.requireManageableProject(any(), any())).thenReturn(project);
        when(projectMembersService.findMembersOf(project)).thenReturn(List.of(projectMember));
        when(projectMembersService.removeMemberFromProject(project, projectMember)).thenReturn(false);

        assertThatThrownBy(() -> memberApiService.removeProjectMember(caller, "77", PERSON_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
    }
}
