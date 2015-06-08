LUPAPISTE.SubmitButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.isSubmitVisible = params.isSubmitVisible;
  self.isSubmitEnabled = params.isSubmitEnabled;
  self.submitCssClasses = params.submitCssClasses || "btn btn-primary";
  self.submitTitle = params.lSubmitTitle ? loc(params.lSubmitTitle) : params.submitTitle;

  self.submit = function() {
    params.submit();
  };

  self.close = function() {
    hub.send("close-dialog");
  };
};
