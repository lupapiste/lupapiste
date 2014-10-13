LUPAPISTE.VerdictSigningModel = function(dialogSelector) {
  "use strict";
  var self = this;

  self.applicationId = null;

  self.dialogSelector = dialogSelector;
  self.password = ko.observable("");
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable("");

  self.init = function(applicationId, verdictId) {
    self.applicationId = applicationId;
    self.verdictId = verdictId;
    console.log("appid: ", applicationId);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

  self.sign = function() {
    console.log("signed verdict");
    var params = {id: self.applicationId, verdictId: self.verdictId};
    console.log("params", params);
    ajax.command("sign-verdict", params)
      .success(function() {
        console.log("great success!");
        LUPAPISTE.ModalDialog.close();
      }).call();
  };
};
