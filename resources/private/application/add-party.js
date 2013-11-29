LUPAPISTE.AddPartyModel = function() {
  "use strict";
  var self = this;

  self.applicationId = null;
  self.partyDocumentNames = ko.observableArray();
  self.documentName = ko.observable();

  self.init = function(model) {
    self.applicationId = model.application.id();
    ajax.query("party-document-names", {id: self.applicationId})
      .success(function(d) {self.partyDocumentNames(ko.mapping.fromJS(d.partyDocumentNames));})
      .call();

    LUPAPISTE.ModalDialog.open("#dialog-add-party");
    return false;
  };

  self.addPartyEnabled = function() {
    return self.documentName();
  };

  self.addParty = function () {
    ajax.command("create-doc", {id: self.applicationId, schemaName: self.documentName()})
      .success(function() { repository.load(self.applicationId); })
      .call();
    return false;
  };
};
