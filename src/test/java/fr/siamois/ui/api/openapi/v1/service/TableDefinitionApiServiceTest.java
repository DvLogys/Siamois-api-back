package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.LangService;
import fr.siamois.ui.api.openapi.v1.resource.table.TableColumnResource;
import fr.siamois.ui.api.openapi.v1.resource.table.TableDefinitionResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableDefinitionApiServiceTest {

    @Mock
    private LangService langService;

    private TableDefinitionApiService service;

    @BeforeEach
    void setUp() {
        // Le service de langue renvoie la clé quand aucun message n'existe : c'est ce comportement
        // qu'on reproduit, pour lire les clés de message dans les assertions.
        when(langService.localeForApiLang(anyString())).thenReturn(Locale.FRENCH);
        when(langService.resolveMessage(any(), any())).thenAnswer(call -> call.getArgument(0));
        service = new TableDefinitionApiService(langService, new FieldResourceApiMapper(langService));
    }

    private TableColumnResource columnOf(TableDefinitionResource definition, String id) {
        return definition.columns().stream()
                .filter(column -> id.equals(column.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Colonne absente : " + id));
    }

    @Test
    void unknownSlugIsNotFound() {
        assertThatThrownBy(() -> service.getDefinition("licorne", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("licorne");
    }

    @Test
    void everyPanelTypeHasColumns() {
        for (PanelResourceTypes type : PanelResourceTypes.values()) {
            assertThat(service.getDefinition(type, "fr").columns())
                    .as("colonnes de %s", type.slug())
                    .isNotEmpty();
        }
    }

    @Test
    void chipColumnLeadsTheListAndCannotBeHidden() {
        TableDefinitionResource definition = service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr");

        TableColumnResource first = definition.columns().get(0);
        assertThat(first.type()).isEqualTo("COMMAND_LINK");
        assertThat(first.toggleable()).isFalse();
        assertThat(first.visible()).isTrue();
        assertThat(first.valueKey()).isEqualTo("fullIdentifier");
    }

    @Test
    void columnsKeepTheOrderAndDefaultVisibilityOfTheJsfDefinition() {
        List<TableColumnResource> columns = service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr").columns();

        assertThat(columns.stream().map(TableColumnResource::id))
                .containsSubsequence("identifierCol", "isPartOf", "contains", "action", "type");
        assertThat(columnOf(service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr"), "type").visible()).isTrue();
        // Masquée par défaut dans la factory, elle doit le rester : une vue enregistrée s'y fie.
        assertThat(columnOf(service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr"), "openingDate").visible())
                .isFalse();
    }

    @Test
    void filterKindFollowsTheFieldType() {
        TableDefinitionResource definition = service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr");

        assertThat(columnOf(definition, "type").filterKind()).isEqualTo("CONCEPT");
        assertThat(columnOf(definition, "author").filterKind()).isEqualTo("PERSON");
        assertThat(columnOf(definition, "action").filterKind()).isEqualTo("ACTION_UNIT");
        assertThat(columnOf(definition, "spatial").filterKind()).isEqualTo("SPATIAL_UNIT");
        assertThat(columnOf(definition, "isPartOf").filterKind()).isEqualTo("RECORDING_UNIT");
        assertThat(columnOf(definition, "openingDate").filterKind()).isEqualTo("DATE_RANGE");
        assertThat(columnOf(definition, "tpq").filterKind()).isEqualTo("NUMBER_RANGE");
        assertThat(columnOf(definition, "matrixColor").filterKind()).isEqualTo("TEXT");
    }

    @Test
    void aFilterableColumnCarriesTheKeyItsFieldBindsTo() {
        TableColumnResource type = columnOf(service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr"), "type");

        assertThat(type.filterable()).isTrue();
        assertThat(type.filterField()).isEqualTo("type");
        assertThat(type.field()).isNotNull();
        assertThat(type.field().valueBinding()).isEqualTo("type");
        assertThat(type.field().answerType()).isEqualTo("SELECT_ONE_FROM_FIELD_CODE");
    }

    @Test
    void syntheticSortKeysAreAnnouncedBecauseTheServiceComputesThem() {
        TableDefinitionResource definition = service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr");

        assertThat(columnOf(definition, "isPartOf").sortField()).isEqualTo("parentsCount");
        assertThat(columnOf(definition, "specimen").sortField()).isEqualTo("specimenCount");
        assertThat(columnOf(definition, "geomorphologicalCycle").sortField())
                .isEqualTo("geomorphologicalCycleLabel");
    }

    /**
     * Le cœur du garde-fou : le JSF déclare bien plus de colonnes triables et filtrables que les
     * requêtes de l'API ne savent traiter, et une colonne annoncée à tort ferait passer une liste
     * entière pour un résultat trié ou restreint.
     */
    @Test
    void aColumnTheListEndpointCannotHandleIsNotAnnouncedSortableOrFilterable() {
        TableColumnResource contributors =
                columnOf(service.getDefinition(PanelResourceTypes.RECORDING_UNIT, "fr"), "contributors");
        assertThat(contributors.sortable()).isFalse();
        assertThat(contributors.sortField()).isNull();
        // Filtrable, elle : le moteur de filtre du domaine sait restreindre sur les contributeurs.
        assertThat(contributors.filterable()).isTrue();

        TableColumnResource specimenCategory =
                columnOf(service.getDefinition(PanelResourceTypes.SPECIMEN, "fr"), "category");
        assertThat(specimenCategory.filterable()).isFalse();
        assertThat(specimenCategory.filterField()).isNull();
        assertThat(specimenCategory.filterKind()).isNull();
    }

    @Test
    void placeColumnsAnnounceOnlyTheSortsTheirEndpointAccepts() {
        TableDefinitionResource definition = service.getDefinition(PanelResourceTypes.SPATIAL_UNIT, "fr");

        assertThat(columnOf(definition, "identifierCol").sortField()).isEqualTo("name");
        // La colonne « projets » se trie par nombre côté JSF ; l'endpoint /places ne le sait pas.
        assertThat(columnOf(definition, "action").sortable()).isFalse();
    }
}
