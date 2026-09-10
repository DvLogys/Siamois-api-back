package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectPerson;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.recordingunit.RecordingUnitSortFilterService;
import fr.siamois.ui.api.openapi.v1.resource.table.TableColumnResource;
import fr.siamois.ui.api.openapi.v1.resource.table.TableDefinitionResource;
import fr.siamois.ui.form.FieldColumn;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.RelationColumn;
import fr.siamois.ui.table.column.TableColumn;
import fr.siamois.ui.table.column.TableColumnType;
import fr.siamois.ui.table.definitions.ActionUnitTableDefinitionFactory;
import fr.siamois.ui.table.definitions.ContainerTableDefinitionFactory;
import fr.siamois.ui.table.definitions.PhaseTableDefinitionFactory;
import fr.siamois.ui.table.definitions.RecordingUnitTableDefinitionFactory;
import fr.siamois.ui.table.definitions.SpatialUnitTableDefinitionFactory;
import fr.siamois.ui.table.definitions.SpecimenTableDefinitionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Les colonnes d'un tableau, telles que les factories du JSF les définissent
 * ({@code ui/table/definitions/*TableDefinitionFactory}), rendues lisibles par un client qui ne
 * rend pas de composant PrimeFaces.
 *
 * Rien n'est redéclaré ici : l'ordre des colonnes, celles visibles par défaut, le champ de
 * formulaire derrière chacune viennent de la définition existante, et la nature du filtre se déduit
 * du sous-type de {@code CustomField} exactement comme {@code filterTemplate.xhtml} choisit sa
 * facette. Une colonne ajoutée à une factory apparaît donc des deux côtés sans autre geste.
 *
 * Une seule chose s'y ajoute : ce que les endpoints de liste savent réellement faire. Le JSF trie
 * et filtre par son propre modèle paresseux, l'API par des requêtes qui n'acceptent qu'un jeu de
 * colonnes ; annoncer un tri ou un filtre que la requête laisserait tomber ferait passer une liste
 * entière pour un résultat trié ou restreint.
 */
@Service
@RequiredArgsConstructor
public class TableDefinitionApiService {

    /** Ce qu'un endpoint de liste sait faire d'une colonne. */
    private record ListCapabilities(Set<String> sortFields, Set<String> filterFields) {

        static final ListCapabilities NONE = new ListCapabilities(Set.of(), Set.of());
    }

    /**
     * Ce qu'une requête peut faire d'une colonne : la clé de tri et la clé de filtre qu'elle
     * accepte, et la nature de la valeur attendue pour ce filtre. Chaque valeur est nulle quand la
     * colonne ne s'y prête pas.
     *
     * Sert des deux côtés du même contrat : la définition renvoyée au client s'en sert pour dire
     * ce qu'il peut demander, et {@code TableQueryApiService} pour n'accepter que cela.
     */
    public record ColumnQuery(String sortField, String filterField, String filterKind) {

        public boolean isSortable() {
            return sortField != null;
        }

        public boolean isFilterable() {
            return filterField != null;
        }
    }

    private final LangService langService;
    private final FieldResourceApiMapper fieldResourceApiMapper;

    public TableDefinitionResource getDefinition(String slug, String lang) {
        PanelResourceTypes type = PanelResourceTypes.fromSlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Type de ressource inconnu : " + slug));
        return getDefinition(type, lang);
    }

    public TableDefinitionResource getDefinition(PanelResourceTypes type, String lang) {
        Locale locale = langService.localeForApiLang(lang);
        TableDefinition definition = definitionOf(type);
        ListCapabilities capabilities = capabilitiesOf(type);

        // La colonne pastille ouvre la fiche : elle mène la ligne dans le JSF (colonne fusionnée
        // statut / identifiant / actions), donc elle mène aussi la liste renvoyée.
        List<TableColumnResource> columns =
                Stream.concat(Stream.ofNullable(definition.getCommandLinkColumn()), definition.getColumns().stream())
                        .map(column -> toResource(column, capabilities, locale))
                        .toList();

        return new TableDefinitionResource(type.slug(), columns);
    }

    private static TableDefinition definitionOf(PanelResourceTypes type) {
        return switch (type) {
            case SPATIAL_UNIT -> SpatialUnitTableDefinitionFactory.definition();
            case ACTION_UNIT -> ActionUnitTableDefinitionFactory.definition();
            case RECORDING_UNIT -> RecordingUnitTableDefinitionFactory.definition();
            case SPECIMEN -> SpecimenTableDefinitionFactory.definition();
            case CONTAINER -> ContainerTableDefinitionFactory.definition();
            case PHASE -> PhaseTableDefinitionFactory.definition();
        };
    }

    private static ListCapabilities capabilitiesOf(PanelResourceTypes type) {
        return switch (type) {
            // Le seul type dont l'endpoint de liste passe par le moteur de tri et de filtre du
            // domaine : les colonnes triables sont celles que le dépôt sait ordonner plus celles
            // que le service calcule, les filtrables celles que ses spécifications déclarent.
            case RECORDING_UNIT -> new ListCapabilities(
                    union(ProjectApiService.ALLOWED_RECORDING_UNIT_SORT_FIELDS,
                            RecordingUnitSortFilterService.supportedSyntheticSortColumns()),
                    RecordingUnitSortFilterService.supportedFilterColumns());
            case SPATIAL_UNIT -> new ListCapabilities(ProjectApiService.ALLOWED_PLACE_SORT_FIELDS, Set.of());
            case ACTION_UNIT -> new ListCapabilities(ProjectApiService.ALLOWED_PROJECT_SORT_FIELDS, Set.of());
            // Mobilier, conteneurs et phases se listent sans tri ni filtre à ce jour.
            case SPECIMEN, CONTAINER, PHASE -> ListCapabilities.NONE;
        };
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        return Stream.concat(first.stream(), second.stream()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Ce qu'une requête de liste accepte pour chaque colonne d'un type — sans résoudre le moindre
     * libellé, puisque le tri et le filtre d'une requête ne s'en servent pas.
     *
     * @param type le type d'entité listé
     * @return une entrée par colonne, dans l'ordre du tableau
     */
    public List<ColumnQuery> queryColumnsOf(PanelResourceTypes type) {
        TableDefinition definition = definitionOf(type);
        ListCapabilities capabilities = capabilitiesOf(type);

        return Stream.concat(Stream.ofNullable(definition.getCommandLinkColumn()), definition.getColumns().stream())
                .map(column -> queryOf(column, capabilities))
                .toList();
    }

    /**
     * Les propriétés métier derrière les colonnes demandées.
     *
     * Sert à savoir ce qu'une requête de liste doit réellement charger : certaines colonnes
     * affichent une collection que la recherche ne ramène pas d'office.
     *
     * @param type      le type d'entité listé
     * @param columnIds les colonnes affichées
     * @return les {@code valueBinding} de leurs champs
     */
    public Set<String> valueBindingsOf(PanelResourceTypes type, Collection<String> columnIds) {
        if (columnIds == null || columnIds.isEmpty()) {
            return Set.of();
        }
        Set<String> wanted = Set.copyOf(columnIds);
        return definitionOf(type).getColumns().stream()
                .filter(column -> wanted.contains(column.getId()))
                .map(column -> bindingOf(column.getField()))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ColumnQuery queryOf(TableColumn column, ListCapabilities capabilities) {
        CustomField field = column.getField();

        // Mêmes règles que `entityDataTable.xhtml` : un tri sans clé propre porte sur la propriété
        // du champ, et un filtre porte sur cette même propriété — sauf la colonne pastille, qui n'a
        // pas de champ derrière elle et que le JSF filtre par sa clé de tri (l'identifiant).
        String declaredSort = column.getSortField() != null && !column.getSortField().isBlank()
                ? column.getSortField()
                : bindingOf(field);
        String declaredFilter = field != null ? bindingOf(field) : declaredSort;

        boolean sortable = column.isSortable() && declaredSort != null
                && capabilities.sortFields().contains(declaredSort);
        boolean filterable = column.isFilterable() && declaredFilter != null
                && capabilities.filterFields().contains(declaredFilter);

        return new ColumnQuery(
                sortable ? declaredSort : null,
                filterable ? declaredFilter : null,
                filterable ? filterKindOf(field) : null);
    }

    private TableColumnResource toResource(TableColumn column, ListCapabilities capabilities, Locale locale) {
        CustomField field = column.getField();
        TableColumnType type = column.getType();
        ColumnQuery query = queryOf(column, capabilities);

        // Seule la colonne pastille n'est jamais masquable : la ligne n'aurait plus de quoi être
        // ouverte.
        return new TableColumnResource(
                column.getId(),
                type.name(),
                langService.resolveMessage(column.getHeaderKey(), locale),
                valueKeyOf(column, field),
                column.isVisible(),
                type != TableColumnType.COMMAND_LINK,
                query.isSortable(),
                query.sortField(),
                query.isFilterable(),
                query.filterKind(),
                query.filterField(),
                field != null && field.getId() != null ? fieldResourceApiMapper.toFieldResource(field, locale) : null,
                column instanceof FieldColumn fieldColumn && fieldColumn.isRequired(),
                column instanceof FieldColumn readable && readable.isReadOnly(),
                iconOf(column),
                column instanceof CommandLinkColumn link ? link.getChipColor() : null,
                column.getWidth(),
                column.getStyleClass());
    }

    private static String bindingOf(CustomField field) {
        return field == null || field.getValueBinding() == null || field.getValueBinding().isBlank()
                ? null
                : field.getValueBinding();
    }

    /** La propriété de la ligne où lire ce que la colonne affiche. */
    private static String valueKeyOf(TableColumn column, CustomField field) {
        if (column instanceof CommandLinkColumn link) {
            return link.getValueKey();
        }
        if (column instanceof RelationColumn relation) {
            return relation.getCountKey();
        }
        return bindingOf(field);
    }

    private static String iconOf(TableColumn column) {
        if (column instanceof CommandLinkColumn link) {
            return link.getIconClass();
        }
        if (column instanceof RelationColumn relation) {
            return relation.getHeaderIcon();
        }
        return null;
    }

    /**
     * La facette de filtre d'une colonne, choisie sur le sous-type de son champ — la dispatch de
     * {@code filterTemplate.xhtml}, reprise telle quelle pour que les deux interfaces proposent la
     * même saisie sur une même colonne.
     */
    private static String filterKindOf(CustomField field) {
        if (field instanceof CustomFieldSelectOneFromFieldCode) return "CONCEPT";
        if (field instanceof CustomFieldSelectPerson) return "PERSON";
        if (field instanceof CustomFieldSelectOneActionUnit) return "ACTION_UNIT";
        if (field instanceof CustomFieldSelectOneSpatialUnit) return "SPATIAL_UNIT";
        if (field instanceof CustomFieldSelectMultipleRecordingUnit) return "RECORDING_UNIT";
        if (field instanceof CustomFieldInteger) return "NUMBER_RANGE";
        if (field instanceof CustomFieldDateTime) return "DATE_RANGE";
        return "TEXT";
    }
}
