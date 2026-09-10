package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.OrganizationMembersServiceInterface;
import fr.siamois.domain.services.ProjectMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.MemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.request.member.MemberCreateRequest;
import fr.siamois.ui.api.openapi.v1.resource.member.MemberResource;
import fr.siamois.ui.api.openapi.v1.resource.member.ProfileSummaryResource;
import fr.siamois.ui.email.InvitationMailer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberApiService {

    private final SettingsAccessApiService settingsAccessApiService;
    private final OrganizationMembersServiceInterface organizationMembersService;
    private final ProjectMembersServiceInterface projectMembersService;
    private final PendingPersonService pendingPersonService;
    private final PersonService personService;
    private final PersonMapper personMapper;
    private final InvitationMailer invitationMailer;
    private final LangService langService;

    // --- Organisation --------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MemberResource> listOrganizationMembers(ProjectApiCaller caller, Long organizationId) {
        InstitutionDTO organization = requireManageableOrganization(caller, organizationId);
        return toResources(organizationMembersService.findMembersOf(organization));
    }

    @Transactional(readOnly = true)
    public List<ProfileSummaryResource> listOrganizationProfiles(ProjectApiCaller caller, Long organizationId) {
        InstitutionDTO organization = requireManageableOrganization(caller, organizationId);
        return organizationMembersService.findAvailableProfiles(organization).stream()
                .map(this::toProfileResource)
                .toList();
    }

    @Transactional
    public MemberResource addOrganizationMember(ProjectApiCaller caller, Long organizationId,
                                                MemberCreateRequest request) {
        InstitutionDTO organization = requireManageableOrganization(caller, organizationId);
        PersonDTO person = requirePerson(request);
        List<ProfileDTO> profiles = resolveProfiles(
                organizationMembersService.findAvailableProfiles(organization), request.profileIds());

        InstitutionMemberDTO member =
                organizationMembersService.addMemberToInstitution(organization, person, profiles);
        return toResource(member, accountStatusOf(person));
    }

    @Transactional
    public void removeOrganizationMember(ProjectApiCaller caller, Long organizationId, Long personId) {
        InstitutionDTO organization = requireManageableOrganization(caller, organizationId);
        InstitutionMemberDTO member = organizationMembersService.findMembersOf(organization).stream()
                .filter(candidate -> candidate.getPerson() != null
                        && java.util.Objects.equals(candidate.getPerson().getId(), personId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));

        // Le service domaine refuse le retrait du dernier gestionnaire : ce refus doit remonter
        // comme un conflit, jamais passer pour un succès.
        if (!organizationMembersService.removeMemberFromInstitution(organization, member)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dernier gestionnaire de l'organisation : retrait impossible");
        }
    }

    @Transactional
    public void resendOrganizationInvitation(ProjectApiCaller caller, Long organizationId, Long personId,
                                             String acceptLanguage) {
        InstitutionDTO organization = requireManageableOrganization(caller, organizationId);
        InstitutionMemberDTO member = organizationMembersService.findMembersOf(organization).stream()
                .filter(candidate -> candidate.getPerson() != null
                        && java.util.Objects.equals(candidate.getPerson().getId(), personId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));

        PersonDTO invitee = member.getPerson();
        if (invitee.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce compte est déjà activé");
        }

        Locale locale = langService.localeForApiLang(ProjectApiService.primaryAcceptLanguage(acceptLanguage));
        PendingPerson pendingPerson = pendingPersonService.resendInvitation(invitee);
        invitationMailer.send(pendingPerson, invitee,
                langService.msg("mail.invitation.scope.institution", locale, organization.getName()),
                langService.msg("mail.invitation.subject", locale, organization.getName()),
                profilesLabel(member.getProfiles(), locale));
    }

    // --- Projet --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MemberResource> listProjectMembers(ProjectApiCaller caller, String projectId) {
        ActionUnitDTO project = requireManageableProject(caller, projectId);
        return toResources(projectMembersService.findMembersOf(project));
    }

    @Transactional(readOnly = true)
    public List<ProfileSummaryResource> listProjectProfiles(ProjectApiCaller caller, String projectId) {
        ActionUnitDTO project = requireManageableProject(caller, projectId);
        return projectMembersService.findAvailableProfiles(project).stream()
                .map(this::toProfileResource)
                .toList();
    }

    @Transactional
    public MemberResource addProjectMember(ProjectApiCaller caller, String projectId, MemberCreateRequest request) {
        ActionUnitDTO project = requireManageableProject(caller, projectId);
        PersonDTO person = requirePerson(request);
        List<ProfileDTO> profiles = resolveProfiles(
                projectMembersService.findAvailableProfiles(project), request.profileIds());

        ProjectMemberDTO member = projectMembersService.addMemberToProject(project, person, profiles);
        return toResource(member, accountStatusOf(person));
    }

    @Transactional
    public void removeProjectMember(ProjectApiCaller caller, String projectId, Long personId) {
        ActionUnitDTO project = requireManageableProject(caller, projectId);
        ProjectMemberDTO member = projectMembersService.findMembersOf(project).stream()
                .filter(candidate -> candidate.getPerson() != null
                        && java.util.Objects.equals(candidate.getPerson().getId(), personId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));

        if (!projectMembersService.removeMemberFromProject(project, member)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dernier gestionnaire du projet : retrait impossible");
        }
    }

    // --- Contrôles d'accès ---------------------------------------------------

    private InstitutionDTO requireManageableOrganization(ProjectApiCaller caller, Long organizationId) {
        return settingsAccessApiService.requireManageableOrganization(caller, organizationId);
    }

    private ActionUnitDTO requireManageableProject(ProjectApiCaller caller, String projectId) {
        return settingsAccessApiService.requireManageableProject(caller, projectId);
    }

    // --- Conversions ---------------------------------------------------------

    private PersonDTO requirePerson(MemberCreateRequest request) {
        if (request == null || request.personId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personId requis");
        }
        try {
            return personMapper.convert(personService.findById(request.personId()));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personne introuvable");
        }
    }

    // Les profils sont choisis parmi ceux de la portée visée : un identifiant venu d'ailleurs
    // ne doit pas pouvoir être attribué ici.
    private List<ProfileDTO> resolveProfiles(List<ProfileDTO> availableProfiles, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.of();
        }
        Set<Long> wanted = Set.copyOf(requestedIds);
        List<ProfileDTO> resolved = availableProfiles.stream()
                .filter(profile -> profile.getId() != null && wanted.contains(profile.getId()))
                .toList();
        if (resolved.size() != wanted.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profil inconnu pour cette portée");
        }
        return resolved;
    }

    private List<MemberResource> toResources(List<? extends MemberDTO> members) {
        Set<Long> personIds = members.stream()
                .map(MemberDTO::getPerson)
                .filter(java.util.Objects::nonNull)
                .map(PersonDTO::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> pending = personIds.isEmpty() ? Set.of()
                : pendingPersonService.findPersonIdsWithPendingInvitation(personIds);
        Set<Long> expired = personIds.isEmpty() ? Set.of()
                : pendingPersonService.findPersonIdsWithExpiredInvitation(personIds);

        return members.stream()
                .map(member -> toResource(member, accountStatusOf(member.getPerson(), pending, expired)))
                .toList();
    }

    private MemberResource toResource(MemberDTO member, String accountStatus) {
        PersonDTO person = member.getPerson();
        List<ProfileSummaryResource> profiles = member.getProfiles() == null ? List.of()
                : member.getProfiles().stream().map(this::toProfileResource).toList();

        return new MemberResource(
                member.getId(),
                person == null ? null : person.getId(),
                person == null ? null : person.getUsername(),
                person == null ? null : person.getName(),
                person == null ? null : person.getLastname(),
                person == null ? null : person.getEmail(),
                profiles,
                accountStatus);
    }

    private ProfileSummaryResource toProfileResource(ProfileDTO profile) {
        return new ProfileSummaryResource(profile.getId(), profile.getCode(), profile.getName());
    }

    // Mêmes états que AbstractMembersListBean : actif, puis invitation expirée, puis invitation en cours.
    private String accountStatusOf(PersonDTO person, Set<Long> pending, Set<Long> expired) {
        if (person == null || person.getId() == null) {
            return "UNKNOWN";
        }
        if (person.isEnabled()) {
            return "ACTIVE";
        }
        if (expired.contains(person.getId())) {
            return "EXPIRED";
        }
        if (pending.contains(person.getId())) {
            return "INVITED";
        }
        return "DISABLED";
    }

    private String accountStatusOf(PersonDTO person) {
        if (person == null || person.getId() == null) {
            return "UNKNOWN";
        }
        Set<Long> ids = Set.of(person.getId());
        return accountStatusOf(person,
                pendingPersonService.findPersonIdsWithPendingInvitation(ids),
                pendingPersonService.findPersonIdsWithExpiredInvitation(ids));
    }

    private String profilesLabel(Collection<ProfileDTO> profiles, Locale locale) {
        String label = profiles == null ? "" : profiles.stream()
                .map(ProfileDTO::getName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
        return StringUtils.isBlank(label) ? langService.msg("mail.invitation.profiles.none", locale) : label;
    }
}
