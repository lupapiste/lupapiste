LUPAPISTE.VerdictSigningModel = function(dialogSelector) {
  "use strict";
  var self = this;

  self.dialogSelector = dialogSelector;
  self.password = ko.observable("");
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable("");
  self.applicationId = null;

  self.init = function(applicationId, verdictId) {
    self.applicationId = applicationId;
    self.verdictId = verdictId;
    console.log("appid: ", applicationId);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

  self.sign = function() {
    var params = {id: self.applicationId, verdictId: self.verdictId};
    ajax.command("sign-verdict", params)
      .success(function() {
        LUPAPISTE.ModalDialog.close();
      }).call();
  };
};
