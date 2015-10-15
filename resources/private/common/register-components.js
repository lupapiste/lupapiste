jQuery(document).ready(function() {
  "use strict";

  var components = [
    {name: "modal-dialog"},
    {name: "message-panel"},
    {name: "fill-info"},
    {name: "foreman-history"},
    {name: "foreman-other-applications"},
    {name: "docgen-group"},
    {name: "docgen-checkbox", model: "docgen-input-model"},
    {name: "docgen-select", model: "docgen-input-model"},
    {name: "docgen-string", model: "docgen-input-model"},
    {name: "docgen-inline-string", model: "docgen-input-model"},
    {name: "attachments-multiselect"},
    {name: "authority-select"},
    {name: "authority-select-dialog"},
    {name: "autocomplete"},
    {name: "export-attachments"},
    {name: "neighbors-owners-dialog"},
    {name: "neighbors-edit-dialog"},
    {name: "company-selector"},
    {name: "company-invite"},
    {name: "company-invite-dialog"},
    {name: "submit-button-group"},
    {name: "yes-no-dialog"},
    {name: "yes-no-button-group"},
    {name: "company-registration-init"},
    {name: "invoice-operator-selector"},
    {name: "ok-dialog"},
    {name: "ok-button-group"},
    {name: "company-edit"},
    {name: "tags-editor"},
    {name: "upload"},
    {name: "openlayers-map"},
    {name: "vetuma-init"},
    {name: "vetuma-status"},
    {name: "help-toggle"},
    {name: "address"},
    {name: "applications-search"},
    {name: "applications-search-tabs"},
    {name: "applications-search-results"},
    {name: "applications-search-filter"},
    {name: "applications-search-filters-list"},
    {name: "applications-search-paging"},
    {name: "applications-foreman-search-filter", model: "applications-search-filter-model"},
    {name: "applications-foreman-search-tabs", template: "applications-search-tabs-template"},
    {name: "applications-foreman-search-filters-list", template: "applications-search-filters-list-template"},
    {name: "applications-foreman-search-results"},
    {name: "autocomplete-tags", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-operations", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-organizations", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-areas", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-handlers"},
    {name: "autocomplete-application-tags", template: "autocomplete-tags-components-template"},
    {name: "add-property"},
    {name: "add-property-dialog"},
    {name: "autocomplete-saved-filters"},
    {name: "indicator"},
    {name: "accordion"},
    {name: "text-field", model: "input-field-model"},
    {name: "checkbox-field", model: "input-field-model"},
    {name: "select-field", model: "input-field-model"},
    {name: "radio-field"},
    {name: "maaraala-tunnus"},
    {name: "property-group"},
    {name: "password-field"},
    {name: "accordion-toolbar"},
    {name: "group-approval"},
    {name: "submit-button"},
    {name: "remove-button"},
    {name: "application-bulletins"},
    {name: "application-bulletins-list"},
    {name: "load-more-application-bulletins"}
  ];

  _.forEach(components, function(component) {
    ko.components.register(component.name, {
      viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name + "Model"))],
      template: { element: (component.template ? component.template : component.name + "-template")}
    });
  });
});
