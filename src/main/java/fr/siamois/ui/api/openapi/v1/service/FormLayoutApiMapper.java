package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.LangService;
import fr.siamois.ui.api.openapi.v1.resource.form.FormLayoutColumnResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormLayoutPanelResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormLayoutRowResource;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Disposition d'un formulaire — sections, lignes et grille — telle que la rend le JSF
 * (`customForm.xhtml` + `customFormPanelContent.xhtml`).
 *
 * Sans elle, un client n'a que la carte plate des réponses et ne peut que les empiler : les
 * sections « Informations générales », « Localisation », « Administratif »… disparaissent.
 */
@Service
@RequiredArgsConstructor
public class FormLayoutApiMapper {

    private final LangService langService;

    public List<FormLayoutPanelResource> toLayout(FormUiDto form, Locale locale) {
        if (form == null || form.getLayout() == null) {
            return List.of();
        }
        return form.getLayout().stream()
                .filter(java.util.Objects::nonNull)
                .map(panel -> toPanel(panel, locale))
                .toList();
    }

    private FormLayoutPanelResource toPanel(CustomFormPanelUiDto panel, Locale locale) {
        List<FormLayoutRowResource> rows = panel.getRows() == null ? List.of()
                : panel.getRows().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(this::toRow)
                        .toList();

        // Les sections système portent un code de message en base : il est résolu ici, le client
        // recevant un titre déjà lisible.
        return new FormLayoutPanelResource(
                langService.resolveMessage(panel.getName(), locale),
                panel.getClassName(),
                rows);
    }

    private FormLayoutRowResource toRow(CustomRowUiDto row) {
        List<FormLayoutColumnResource> columns = row.getColumns() == null ? List.of()
                : row.getColumns().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(this::toColumn)
                        .toList();
        return new FormLayoutRowResource(columns);
    }

    private FormLayoutColumnResource toColumn(CustomColUiDto column) {
        // Même clé que celle des réponses (`String.valueOf(field.getId())`), sans quoi le client ne
        // saurait pas relier une cellule du layout à sa valeur.
        String fieldId = column.getField() == null || column.getField().getId() == null
                ? null
                : String.valueOf(column.getField().getId());

        return new FormLayoutColumnResource(
                fieldId,
                column.getClassName(),
                column.isRequired(),
                column.isReadOnly());
    }
}
