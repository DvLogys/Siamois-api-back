package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.FilterDTO;
import fr.siamois.ui.api.openapi.v1.service.TableDefinitionApiService.ColumnQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le tri et les filtres d'une requête de liste, lus à travers la définition des colonnes du type.
 *
 * C'est le pendant serveur de ce que la définition annonce : une colonne y est déclarée triable ou
 * filtrable, et c'est ici que la valeur reçue pour cette colonne est convertie en ce que le moteur
 * de filtre du domaine attend. Les deux lisent la même déclaration, donc ce que le client peut
 * demander et ce que le serveur sait appliquer ne peuvent pas diverger.
 *
 * Format attendu, celui qu'émet déjà le SPA : {@code sort=champ:asc|desc} et un paramètre
 * {@code filter} répétable de la forme {@code champ:valeur}, où la valeur est un texte, une liste
 * d'identifiants séparés par des virgules, ou un intervalle {@code début..fin} dont chaque borne
 * peut manquer.
 */
@Service
@RequiredArgsConstructor
public class TableQueryApiService {

    private static final String RANGE_SEPARATOR = "..";

    private final TableDefinitionApiService tableDefinitionApiService;

    /**
     * Le tri demandé, ramené à ce que la colonne visée autorise.
     *
     * Un tri inconnu retombe sur le tri par défaut, comme partout ailleurs dans l'API : afficher
     * les mêmes lignes dans un autre ordre reste une réponse juste.
     *
     * @param type        le type d'entité listé
     * @param sortParam   le paramètre reçu, {@code champ:direction}
     * @param defaultSort le tri à appliquer faute de mieux
     * @return le tri à passer au dépôt
     */
    public Sort sortOf(PanelResourceTypes type, String sortParam, Sort defaultSort) {
        if (sortParam == null || sortParam.isBlank()) {
            return defaultSort;
        }
        String[] parts = sortParam.split(":", 2);
        String requested = parts[0].trim();

        boolean sortable = tableDefinitionApiService.queryColumnsOf(type).stream()
                .filter(ColumnQuery::isSortable)
                .anyMatch(column -> column.sortField().equals(requested));
        if (!sortable) {
            return defaultSort;
        }

        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, requested);
    }

    /**
     * Les filtres demandés, convertis pour le moteur de filtre du domaine.
     *
     * Un filtre portant sur une colonne que la liste ne sait pas restreindre est refusé plutôt
     * qu'ignoré : contrairement à un tri, un filtre laissé de côté renvoie d'autres lignes que
     * celles demandées, et rien ne le signalerait.
     *
     * @param type          le type d'entité listé
     * @param filterParams  les paramètres {@code filter} reçus
     * @return les filtres à passer au service de recherche
     */
    public FilterDTO filtersOf(PanelResourceTypes type, List<String> filterParams) {
        FilterDTO filters = new FilterDTO();
        if (filterParams == null || filterParams.isEmpty()) {
            return filters;
        }

        Map<String, ColumnQuery> filterable = tableDefinitionApiService.queryColumnsOf(type).stream()
                .filter(ColumnQuery::isFilterable)
                .collect(Collectors.toMap(ColumnQuery::filterField, Function.identity(), (first, second) -> first));

        for (String param : filterParams) {
            if (param == null || param.isBlank()) {
                continue;
            }
            String[] parts = param.split(":", 2);
            String field = parts[0].trim();
            String rawValue = parts.length > 1 ? parts[1].trim() : "";

            ColumnQuery column = filterable.get(field);
            if (column == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Colonne non filtrable pour " + type.slug() + " : " + field);
            }
            if (rawValue.isEmpty()) {
                continue;
            }
            applyFilter(filters, column, rawValue);
        }

        return filters;
    }

    private static void applyFilter(FilterDTO filters, ColumnQuery column, String rawValue) {
        String field = column.filterField();
        switch (column.filterKind()) {
            case "CONCEPT", "PERSON", "ACTION_UNIT", "SPATIAL_UNIT", "RECORDING_UNIT" ->
                    filters.add(field, idsOf(field, rawValue), FilterDTO.FilterType.EQUAL);
            case "DATE_RANGE" -> filters.add(field, dateRangeOf(field, rawValue), FilterDTO.FilterType.EQUAL);
            case "NUMBER_RANGE" -> filters.add(field, intRangeOf(field, rawValue), FilterDTO.FilterType.EQUAL);
            default -> filters.add(field, rawValue, FilterDTO.FilterType.CONTAINS);
        }
    }

    private static List<Long> idsOf(String field, String rawValue) {
        try {
            return Arrays.stream(rawValue.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException e) {
            throw badFilter(field, "une liste d'identifiants séparés par des virgules", rawValue);
        }
    }

    /**
     * Un intervalle de dates. La borne haute couvre la journée entière : la saisie porte sur des
     * jours, alors que la valeur comparée porte une heure, et s'arrêter à minuit exclurait tout ce
     * qui a été enregistré le dernier jour de l'intervalle.
     */
    private static List<OffsetDateTime> dateRangeOf(String field, String rawValue) {
        String[] bounds = boundsOf(rawValue);
        try {
            OffsetDateTime from = bounds[0].isEmpty() ? null
                    : LocalDate.parse(bounds[0]).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
            OffsetDateTime to = bounds[1].isEmpty() ? null
                    : LocalDate.parse(bounds[1]).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            return Arrays.asList(from, to);
        } catch (DateTimeParseException e) {
            throw badFilter(field, "un intervalle de dates AAAA-MM-JJ..AAAA-MM-JJ", rawValue);
        }
    }

    private static List<Integer> intRangeOf(String field, String rawValue) {
        String[] bounds = boundsOf(rawValue);
        try {
            Integer from = bounds[0].isEmpty() ? null : Integer.valueOf(bounds[0]);
            Integer to = bounds[1].isEmpty() ? null : Integer.valueOf(bounds[1]);
            return Arrays.asList(from, to);
        } catch (NumberFormatException e) {
            throw badFilter(field, "un intervalle de nombres min..max", rawValue);
        }
    }

    /** Les deux bornes d'un intervalle, dont chacune peut être vide : {@code a..b}, {@code ..b}, {@code a..}. */
    private static String[] boundsOf(String rawValue) {
        int separator = rawValue.indexOf(RANGE_SEPARATOR);
        if (separator < 0) {
            return new String[]{rawValue.trim(), ""};
        }
        return new String[]{
                rawValue.substring(0, separator).trim(),
                rawValue.substring(separator + RANGE_SEPARATOR.length()).trim()};
    }

    private static ResponseStatusException badFilter(String field, String expected, String rawValue) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Filtre invalide sur " + field + " : " + expected + " attendu, reçu « " + rawValue + " »");
    }
}
