package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.LangService;
import fr.siamois.dto.FilterDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableQueryApiServiceTest {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "creationTime");

    @Mock
    private LangService langService;

    private TableQueryApiService service;

    @BeforeEach
    void setUp() {
        service = new TableQueryApiService(
                new TableDefinitionApiService(langService, new FieldResourceApiMapper(langService)));
    }

    private FilterDTO filters(String... params) {
        return service.filtersOf(PanelResourceTypes.RECORDING_UNIT, List.of(params));
    }

    @Test
    void anEmptyRequestFiltersNothing() {
        assertThat(service.filtersOf(PanelResourceTypes.RECORDING_UNIT, null).getColumns()).isEmpty();
        assertThat(filters().getColumns()).isEmpty();
    }

    @Test
    void aTextFilterMatchesOnContent() {
        FilterDTO result = filters("matrixColor:brun");

        assertThat(result.containsColumn("matrixColor")).isTrue();
        assertThat(result.valueOfAsString("matrixColor")).isEqualTo("brun");
        assertThat(result.filterOf("matrixColor").getType()).isEqualTo(FilterDTO.FilterType.CONTAINS);
    }

    @Test
    void aSelectionFilterBecomesTheListOfIdentifiersTheEngineExpects() {
        FilterDTO result = filters("type:12,15", "author:7");

        assertThat(result.valueAsIdListOf("type")).containsExactly(12L, 15L);
        assertThat(result.valueAsIdListOf("author")).containsExactly(7L);
    }

    /**
     * La borne haute couvre la journée entière : s'arrêter à minuit exclurait tout ce qui a été
     * enregistré le dernier jour de l'intervalle demandé.
     */
    @Test
    void aDateRangeCoversBothEndDaysEntirely() {
        FilterDTO.DateRange range = filters("openingDate:2024-03-01..2024-03-31")
                .valueAsDateRangeOf("openingDate");

        assertThat(range.from()).isNotNull();
        assertThat(range.from().toLocalTime().toSecondOfDay()).isZero();
        assertThat(range.to()).isNotNull();
        assertThat(range.to().toLocalDate()).isEqualTo("2024-03-31");
        assertThat(range.to().getHour()).isEqualTo(23);
    }

    @Test
    void anOpenRangeLeavesTheMissingBoundNull() {
        FilterDTO.DateRange from = filters("openingDate:2024-03-01..").valueAsDateRangeOf("openingDate");
        assertThat(from.from()).isNotNull();
        assertThat(from.to()).isNull();

        FilterDTO.DateRange to = filters("openingDate:..2024-03-31").valueAsDateRangeOf("openingDate");
        assertThat(to.from()).isNull();
        assertThat(to.to()).isNotNull();

        FilterDTO.IntRange upTo = filters("tpq:..500").valueAsIntRangeOf("tpq");
        assertThat(upTo.from()).isNull();
        assertThat(upTo.to()).isEqualTo(500);
    }

    @Test
    void aNumberRangeBecomesItsTwoBounds() {
        FilterDTO.IntRange range = filters("tpq:-50..120").valueAsIntRangeOf("tpq");

        assertThat(range.from()).isEqualTo(-50);
        assertThat(range.to()).isEqualTo(120);
    }

    @Test
    void anEmptyValueIsNotAFilter() {
        assertThat(filters("type:", "  ").getColumns()).isEmpty();
    }

    /**
     * Un filtre ignoré renverrait d'autres lignes que celles demandées sans que rien ne le
     * signale : il vaut mieux refuser la requête.
     */
    @Test
    void filteringOnAColumnTheListCannotRestrictIsRefused() {
        assertThatThrownBy(() -> filters("phases:3"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("phases");

        assertThatThrownBy(() -> service.filtersOf(PanelResourceTypes.SPECIMEN, List.of("category:1")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anUnreadableFilterValueIsRefused() {
        assertThatThrownBy(() -> filters("type:douze"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("identifiants");
        assertThatThrownBy(() -> filters("openingDate:hier..demain"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dates");
        assertThatThrownBy(() -> filters("tpq:beaucoup"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("nombres");
    }

    @Test
    void anAnnouncedSortIsApplied() {
        Sort sort = service.sortOf(PanelResourceTypes.RECORDING_UNIT, "fullIdentifier:asc", DEFAULT_SORT);

        assertThat(sort).containsExactly(new Sort.Order(Sort.Direction.ASC, "fullIdentifier"));
    }

    @Test
    void aComputedSortKeyIsAcceptedToo() {
        Sort sort = service.sortOf(PanelResourceTypes.RECORDING_UNIT, "specimenCount:desc", DEFAULT_SORT);

        assertThat(sort).containsExactly(new Sort.Order(Sort.Direction.DESC, "specimenCount"));
    }

    /** Contrairement à un filtre, un tri inconnu retombe sur le défaut : les lignes restent justes. */
    @Test
    void anUnknownSortFallsBackToTheDefault() {
        assertThat(service.sortOf(PanelResourceTypes.RECORDING_UNIT, "contributors:asc", DEFAULT_SORT))
                .isEqualTo(DEFAULT_SORT);
        assertThat(service.sortOf(PanelResourceTypes.RECORDING_UNIT, "licorne:asc", DEFAULT_SORT))
                .isEqualTo(DEFAULT_SORT);
        assertThat(service.sortOf(PanelResourceTypes.RECORDING_UNIT, null, DEFAULT_SORT))
                .isEqualTo(DEFAULT_SORT);
    }

    @Test
    void aSortWithNoDirectionGoesAscending() {
        assertThat(service.sortOf(PanelResourceTypes.RECORDING_UNIT, "openingDate", DEFAULT_SORT))
                .containsExactly(new Sort.Order(Sort.Direction.ASC, "openingDate"));
    }

    @Test
    void datesAreReadAsOffsetsSoTheEngineNeedsNoConversion() {
        Object raw = filters("openingDate:2024-03-01..2024-03-31").valueOf("openingDate");

        assertThat(raw).isInstanceOf(List.class);
        assertThat((List<?>) raw).allSatisfy(bound -> assertThat(bound).isInstanceOf(OffsetDateTime.class));
    }
}
