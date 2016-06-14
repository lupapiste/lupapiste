LUPAPISTE.SubmitButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  var p = params || {};
  self.isSubmitVisible = p.isSubmitVisible || true;
  self.isSubmitEnabled = p.isSubmitEnabled || true;
  self.submitCssClasses = p.submitCssClasses || "btn btn-primary";
  self.submitTitle = p.lSubmitTitle ? loc(p.lSubmitTitle) : p.submitTitle;
  self.submit = p.submit || _.noop;

};
