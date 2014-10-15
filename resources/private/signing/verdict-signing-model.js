LUPAPISTE.VerdictSigningModel = function(dialogSelector) {
  "use strict";
  var self = this;

  self.dialogSelector = dialogSelector;
  self.password = ko.observable("");
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable("");
  self.applicationId = null;
  self.signed = ko.observable(false);

  self.authorizationModel = authorization.create();

  self.init = function(application, verdictId) {
    self.applicationId = application.id();
    self.password("");
    self.verdictId = verdictId;
    self.processing(false);
    self.pending(false);
    self.errorMessage("");

    self.authorizationModel.refresh(application.id());

    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

  self.sign = function() {
    self.errorMessage("");
    ajax.command("sign-verdict", {id: self.applicationId, verdictId: self.verdictId, password: self.password()})
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        self.password("");
        repository.load(self.applicationId);
        LUPAPISTE.ModalDialog.close();
      })
      .error(function(e) { self.errorMessage(e.text); })
      .call();
  };
};
