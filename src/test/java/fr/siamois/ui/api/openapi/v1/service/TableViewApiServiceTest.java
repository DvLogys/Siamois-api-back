package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.infrastructure.database.UiViewRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.UITableViewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableViewApiServiceTest {

    private static final long OWNER_ID = 5L;
    private static final long OTHER_PERSON_ID = 6L;
    private static final long VIEW_ID = 100L;

    @Mock
    private UiViewRepository uiViewRepository;
    @Mock
    private UITableViewMapper uiTableViewMapper;
    @Mock
    private PersonMapper personMapper;

    private TableViewApiService tableViewApiService;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        tableViewApiService = new TableViewApiService(uiViewRepository, uiTableViewMapper, personMapper);
        PersonDTO person = new PersonDTO();
        person.setId(OWNER_ID);
        caller = new ProjectApiCaller(person, Set.of(), List.of());
        when(uiTableViewMapper.toDto(any())).thenReturn(new UITableViewDTO());
    }

    private UiTableView viewOwnedBy(long personId) {
        Person owner = new Person();
        owner.setId(personId);
        UiTableView view = new UiTableView();
        view.setId(VIEW_ID);
        view.setOwner(owner);
        view.setResourceType("recording-unit");
        return view;
    }

    private UITableViewDTO validRequest() {
        UITableViewDTO request = new UITableViewDTO();
        request.setResourceType("recording-unit");
        request.setState(new TableViewState());
        return request;
    }

    /** Une vue est personnelle : celle d'un autre doit être indiscernable d'une vue inexistante. */
    @Test
    void getOwnView_hidesViewsOwnedBySomeoneElseBehindNotFound() {
        when(uiViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(viewOwnedBy(OTHER_PERSON_ID)));

        assertThatThrownBy(() -> tableViewApiService.getOwnView(caller, VIEW_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void updateView_refusesToOverwriteAViewOwnedBySomeoneElse() {
        when(uiViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(viewOwnedBy(OTHER_PERSON_ID)));

        assertThatThrownBy(() -> tableViewApiService.updateView(caller, VIEW_ID, validRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(uiViewRepository, never()).save(any());
    }

    @Test
    void deleteView_refusesAViewOwnedBySomeoneElse() {
        when(uiViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(viewOwnedBy(OTHER_PERSON_ID)));

        assertThatThrownBy(() -> tableViewApiService.deleteView(caller, VIEW_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(uiViewRepository, never()).delete(any());
    }

    /** Le propriétaire vient de l'entité en base, jamais du corps de la requête. */
    @Test
    void updateView_keepsTheStoredOwnerEvenIfTheRequestCarriesAnother() {
        UiTableView stored = viewOwnedBy(OWNER_ID);
        when(uiViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(stored));
        when(uiViewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UITableViewDTO request = validRequest();
        PersonDTO usurper = new PersonDTO();
        usurper.setId(OTHER_PERSON_ID);
        request.setOwner(usurper);
        request.setTitle("Ma vue");

        tableViewApiService.updateView(caller, VIEW_ID, request);

        ArgumentCaptor<UiTableView> saved = ArgumentCaptor.forClass(UiTableView.class);
        verify(uiViewRepository).save(saved.capture());
        assertThat(saved.getValue().getOwner().getId()).isEqualTo(OWNER_ID);
        assertThat(saved.getValue().getTitle()).isEqualTo("Ma vue");
    }

    @Test
    void createView_assignsTheCallerAsOwner() {
        Person callerPerson = new Person();
        callerPerson.setId(OWNER_ID);
        when(personMapper.invertConvert(caller.person())).thenReturn(callerPerson);
        when(uiViewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        tableViewApiService.createView(caller, validRequest());

        ArgumentCaptor<UiTableView> saved = ArgumentCaptor.forClass(UiTableView.class);
        verify(uiViewRepository).save(saved.capture());
        assertThat(saved.getValue().getOwner().getId()).isEqualTo(OWNER_ID);
    }

    @Test
    void listOwnViews_requiresAResourceType() {
        assertThatThrownBy(() -> tableViewApiService.listOwnViews(caller, "  "))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void createView_requiresAState() {
        UITableViewDTO request = new UITableViewDTO();
        request.setResourceType("recording-unit");

        assertThatThrownBy(() -> tableViewApiService.createView(caller, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }
}
