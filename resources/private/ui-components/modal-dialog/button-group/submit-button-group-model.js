LUPAPISTE.SubmitButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.isSubmitVisible = params.isSubmitVisible;
  self.isSubmitEnabled = params.isSubmitEnabled;
  self.submitTitle = params.submitTitle;

  self.submit = function() {
    params.submit();
  };

  self.close = function() {
    hub.send("close-dialog");
  };
};
