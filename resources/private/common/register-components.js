jQuery(document).ready(function() {
  "use strict";

  var components = [
    {name: "modal-dialog"},
    {name: "message-panel"},
    {name: "approval", synchronous: true},
    {name: "fill-info", synchronous: true},
    {name: "foreman-history", synchronous: true},
    {name: "foreman-other-applications", synchronous: true},
    {name: "docgen-group", synchronous: true},
    {name: "docgen-repeating-group", synchronous: true},
    {name: "docgen-table", synchronous: true},
    {name: "docgen-huoneistot-table", synchronous: true},
    {name: "docgen-input", synchronous: true},
    {name: "docgen-string", model: "docgen-input-model", template: "docgen-input-template", synchronous: true},
    {name: "docgen-checkbox", model: "docgen-input-model", template: "docgen-input-template", synchronous: true},
    {name: "docgen-text", model: "docgen-input-model", template: "docgen-input-template", synchronous: true},
    {name: "docgen-select", synchronous: true},
    {name: "docgen-button", synchronous: true},
    {name: "docgen-date", synchronous: true},
    {name: "docgen-time", template: "docgen-input-template", synchronous: true},
    {name: "docgen-review-buildings", synchronous: true},
    {name: "docgen-building-select", synchronous: true},
    {name: "docgen-person-select", synchronous: true},
    {name: "docgen-funding-select", synchronous: true},
    {name: "construction-waste-report", synchronous: true},
    {name: "attachments-multiselect"},
    {name: "attachment-details"},
    {name: "attachment-page-stamping"},
    {name: "attachments-change-type"},
    {name: "base-autocomplete", model: "autocomplete-base-model"},
    {name: "autocomplete"},
    {name: "export-attachments"},
    {name: "neighbors-owners-dialog"},
    {name: "neighbors-edit-dialog"},
    {name: "company-selector"},
    {name: "company-invite"},
    {name: "company-invite-dialog"},
    {name: "submit-button-group"},
    {name: "yes-no-dialog"},
    {name: "yes-no-select-dialog"},
    {name: "textarea-dialog"},
    {name: "company-approve-invite-dialog"},
    {name: "yes-no-button-group"},
    {name: "company-registration-init"},
    {name: "invoice-operator-selector"},
    {name: "ok-dialog"},
    {name: "ok-button-group"},
    {name: "integration-error-dialog"},
    {name: "remove-invitation-denied-company-error-dialog"},
    {name: "company-edit"},
    {name: "organization-name-editor"},
    {name: "tags-editor"},
    {name: "company-tags-editor", template: "tags-editor-template"},
    {name: "area-upload"},
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
    {name: "applications-company-search-filter"},
    {name: "applications-company-search-tabs", template: "applications-search-tabs-template"},
    {name: "applications-foreman-search-filters-list", template: "applications-search-filters-list-template"},
    {name: "applications-company-search-filters-list", template: "applications-search-filters-list-template"},
    {name: "applications-foreman-search-results"},
    {name: "assignments-search-tabs", template: "applications-search-tabs-template"},
    {name: "assignments-search-results"},
    {name: "assignments-search-filter"},
    {name: "automatic-assignments"},
    {name: "autocomplete-tags", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-company-tags", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-operations", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-organizations", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-areas", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-handlers"},
    {name: "autocomplete-recipient"},
    {name: "autocomplete-application-tags", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-application-company-tags", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-assignment-targets", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-event", template: "autocomplete-tags-components-template"},
    {name: "autocomplete-triggers-target", template: "autocomplete-triggers-target-template"},
    {name: "add-property"},
    {name: "add-property-dialog"},
    {name: "autocomplete-saved-filters"},
    {name: "indicator"},
    {name: "indicator-icon"},
    {name: "accordion"},
    {name: "date-field", model: "input-field-model"},
    {name: "text-field", model: "input-field-model"},
    {name: "textarea-field", model: "input-field-model"},
    {name: "checkbox-field", model: "input-field-model"},
    {name: "select-field"},
    {name: "radio-field"},
    {name: "search-field"},
    {name: "maaraala-tunnus", synchronous: true},
    {name: "property-group", synchronous: true},
    {name: "link-permit-selector"},
    {name: "password-field"},
    {name: "accordion-toolbar", synchronous: true},
    {name: "group-approval", synchronous: true},
    {name: "submit-button"},
    {name: "remove-button"},
    {name: "field-reset-button"},
    {name: "publish-application"},
    {name: "move-to-proclaimed"},
    {name: "move-to-verdict-given"},
    {name: "move-to-final"},
    {name: "ymp-bulletin-versions"},
    {name: "ymp-bulletin-tab"},
    {name: "ymp-bulletin-comments"},
    {name: "infinite-scroll"},
    {name: "statements-tab"},
    {name: "statements-table"},
    {name: "statement-edit"},
    {name: "statement-edit-reply"},
    {name: "statement-reply-request"},
    {name: "statement-control-buttons"},
    {name: "guest-authorities"},
    {name: "bubble-dialog"},
    {name: "application-guests"},
    {name: "side-panel"},
    {name: "conversation"},
    {name: "authority-notice"},
    {name: "company-notes-panel"},
    {name: "authorized-parties"},
    {name: "person-invite"},
    {name: "company-invite-bubble"},
    {name: "operation-editor"},
    {name: "document-identifier"},
    {name: "building-identifier"},
    {name: "change-state"},
    {name: "verdict-appeal"},
    {name: "verdict-appeal-bubble"},
    {name: "file-upload"},
    {name: "form-cell"},
    {name: "cell-text"},
    {name: "cell-span"},
    {name: "cell-textarea", model: "cell-model"},
    {name: "cell-date"},
    {name: "cell-select"},
    {name: "review-tasks"},
    {name: "task"},
    {name: "ram-links"},
    {name: "attachments-listing"},
    {name: "attachments-accordions"},
    {name: "attachments-listing-accordion"},
    {name: "attachments-table"},
    {name: "attachments-operation-buttons"},
    {name: "attachment-type-group-autocomplete"},
    {name: "attachment-type-autocomplete"},
    {name: "attachment-group-autocomplete"},
    {name: "attachment-backendid-autocomplete"},
    {name: "attachments-require"},
    {name: "attachments-require-bubble"},
    {name: "rollup"},
    {name: "rollup-button"},
    {name: "rollup-status-button"},
    {name: "filters"},
    {name: "suti-display"},
    {name: "change-email"},
    {name: "side-panel-info"},
    {name: "info-link"},
    {name: "targeted-attachments"},
    {name: "open-3d-map"},
    {name: "extension-applications"},
    {name: "create-assignment"},
    {name: "organization-links"},
    {name: "accordion-assignments"},
    {name: "assignment-editor"},
    {name: "attachment-type-id"},
    {name: "state-icons"},
    {name: "docgen-calculation"},
    {name: "docgen-footer-sum"},
    {name: "reject-note"},
    {name: "upload-button"},
    {name: "upload-link"},
    {name: "attachment-upload"},
    {name: "attachment-batch"},
    {name: "upload-progress"},
    {name: "icon-button"},
    {name: "toggle"},
    {name: "combobox"},
    {name: "drop-zone"},
    {name: "upload-zone"},
    {name: "link-permit-autocomplete"},
    {name: "handler-list"},
    {name: "card"},
    {name: "edit-handlers"},
    {name: "register-company-steps"},
    {name: "register-company-buttons"},
    {name: "register-company-account-type"},
    {name: "register-company-info"},
    {name: "register-company-summary"},
    {name: "cell-invoice-operator"},
    {name: "register-company-sign"},
    {name: "set-password"},
    {name: "login"},
    {name: "campaign-editor"},
    {name: "date-editor"},
    {name: "mark-review-faulty-dialog"},
    {name: "backend-id-manager"},
    {name: "generic-bulletin-tab"},
    {name: "add-link-permit"},
    {name: "premises-upload", template: "premises-upload-template"}
];

  ko.registerLupapisteComponents(components);
});
