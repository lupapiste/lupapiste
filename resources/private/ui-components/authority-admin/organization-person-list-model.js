// A component for handling lists of people in the users page.
// Allows the user to add, edit and remove people through a simple list interface.
// At the moment used for statement giver and review officer lists.
//
// Params [optional]:
// listed               A string containing the common substring in all of the component's elements, e.g. "statement-giver"
// ltextHeader          The header translation key
// ltextColumns         The field header translation keys
// ltextAdd             The key for the "Add a statement giver" - text
// ltextEdit            The key for the header of the editing modal window
// [ltextUseList]       The key for the checkbox that enables/disables this list; if this is undefined, the checkbox is not displayed
// [useListObservable]  The knockout observable for the organization model's toggle for whether the list is in use
// fields               The list of fields each row should include
// disabledFields       The list of fields that the user should not be able to edit once created
// people               The list of person data for each row
// model                The PersonListModel for this list (from authority-admin.js)

LUPAPISTE.OrganizationPersonListModel = function(params) {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.listed = params.listed;
  self.ltextHeader = params.ltextHeader;
  self.ltextColumns = params.ltextColumns;
  self.ltextAdd = params.ltextAdd;
  self.ltextEdit = params.ltextEdit;
  self.ltextUseList = params.ltextUseList;

  self.fields = params.fields;
  self.disabledFields = params.disabledFields;
  self.fieldsWithLabels = _.zip(params.fields, params.ltextColumns);

  self.people = params.people;

  self.deleteFunction = params.model.deletePerson;
  self.createFunction = params.model.openCreateModal;
  self.editFunction = params.model.openEditModal;
  self.modelCreate = params.model.modelCreate;
  self.modelEdit = params.model.modelEdit;
  self.model = params.model;


  self.showUseListCheckbox = Boolean(params.ltextUseList);
  self.isListEnabled = ko.observable(true);
  if (typeof params.useListObservable !== "undefined") {
    self.isListEnabled = params.useListObservable;
  }

  // Returns the element id for the given field (used for both the field and its label)
  self.getId = function(modalDialogType, fieldName) {
    return modalDialogType + "-" + self.listed + "-" + fieldName;
  };

  // Returns true if the given field should be enabled
  self.isEnabled = function(modalDialogType, fieldName) {
    return !_.has(self.disabledFields, modalDialogType)
              || !_.includes(self.disabledFields[modalDialogType], fieldName);
  };

  // A map of all modal dialogs for this component for knockout loop
  self.modalDialogs = [
    {type: "create", model: self.modelCreate, ltext: self.ltextAdd},
    {type: "edit", model: self.modelEdit, ltext: self.ltextEdit}
  ];

  // Called when the box "Use review officer list" is clicked
  self.toggleUseList = function(disabled) {
    ajax
      .command("set-organization-" + self.listed + "s-list-enabled", { enabled: !disabled })
      .success(function() {
        self.isListEnabled(!disabled);
        self.model.load();
      })
      .call();
  };
};
