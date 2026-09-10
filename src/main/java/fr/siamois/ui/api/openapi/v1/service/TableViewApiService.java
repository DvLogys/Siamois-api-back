package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.infrastructure.database.UiViewRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.UITableViewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TableViewApiService {

    private final UiViewRepository uiViewRepository;
    private final UITableViewMapper uiTableViewMapper;
    private final PersonMapper personMapper;

    @Transactional(readOnly = true)
    public List<UITableViewDTO> listOwnViews(ProjectApiCaller caller, String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceType requis");
        }
        return uiViewRepository
                .findAllByOwnerIdAndResourceTypeOrderByTitleAsc(caller.person().getId(), resourceType.trim())
                .stream()
                .map(uiTableViewMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UITableViewDTO getOwnView(ProjectApiCaller caller, Long viewId) {
        return uiTableViewMapper.toDto(requireOwnView(caller, viewId));
    }

    @Transactional
    public UITableViewDTO createView(ProjectApiCaller caller, UITableViewDTO request) {
        requireResourceTypeAndState(request);

        UiTableView entity = new UiTableView();
        entity.setResourceType(request.getResourceType().trim());
        entity.setTitle(request.getTitle());
        entity.setState(request.getState());
        entity.setOwner(personMapper.invertConvert(caller.person()));
        return uiTableViewMapper.toDto(uiViewRepository.save(entity));
    }

    @Transactional
    public UITableViewDTO updateView(ProjectApiCaller caller, Long viewId, UITableViewDTO request) {
        requireResourceTypeAndState(request);

        // On repart de l'entité possédée : le propriétaire et l'identifiant ne viennent jamais du corps
        // de la requête, sinon une vue d'un autre utilisateur pourrait être réécrite.
        UiTableView entity = requireOwnView(caller, viewId);
        entity.setResourceType(request.getResourceType().trim());
        entity.setTitle(request.getTitle());
        entity.setState(request.getState());
        return uiTableViewMapper.toDto(uiViewRepository.save(entity));
    }

    @Transactional
    public void deleteView(ProjectApiCaller caller, Long viewId) {
        uiViewRepository.delete(requireOwnView(caller, viewId));
    }

    private UiTableView requireOwnView(ProjectApiCaller caller, Long viewId) {
        UiTableView entity = uiViewRepository.findById(viewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vue introuvable"));

        Long ownerId = entity.getOwner() == null ? null : entity.getOwner().getId();
        if (ownerId == null || !Objects.equals(ownerId, caller.person().getId())) {
            // Une vue de tableau est personnelle : on ne révèle pas l'existence de celle d'un autre.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vue introuvable");
        }
        return entity;
    }

    private void requireResourceTypeAndState(UITableViewDTO request) {
        if (request == null || request.getResourceType() == null || request.getResourceType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceType requis");
        }
        TableViewState state = request.getState();
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state requis");
        }
    }
}
