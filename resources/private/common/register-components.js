jQuery(document).ready(function() {
  "use strict";

  var components = [
    {name: "modal-dialog"},
    {name: "message-panel"},
    {name: "fill-info"},
    {name: "foreman-history"},
    {name: "foreman-other-applications"},
    {name: "docgen-checkbox"},
    {name: "docgen-select"},
    {name: "docgen-string"},
    {name: "docgen-group"},
    {name: "docgen-inline-string"},
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
    {name: "applications-search-results"},
    {name: "applications-search-filter"},
    {name: "applications-search-paging"},
    {name: "autocomplete-tags", template: "autocomplete-tags-components"},
    {name: "autocomplete-operations", template: "autocomplete-tags-components"},
    {name: "autocomplete-organizations", template: "autocomplete-tags-components"},
    {name: "autocomplete-areas", template: "autocomplete-tags-components"},
    {name: "autocomplete-handlers"},
    {name: "autocomplete-application-tags", template: "autocomplete-tags-components"},
    {name: "add-property"},
    {name: "add-property-dialog"},
    {name: "indicator"},
    {name: "accordion"},
    {name: "text-field", model: "input-field"},
    {name: "checkbox-field", model: "input-field"},
    {name: "select-field", model: "input-field"},
    {name: "password-field"},
    {name: "maaraala-tunnus"},
    {name: "property-group"}
  ];

  _.forEach(components, function(component) {
    ko.components.register(component.name, {
      viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name)) + "Model"],
      template: { element: (component.template ? component.template : component.name) + "-template"}
    });
  });
});
