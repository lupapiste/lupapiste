LUPAPISTE.YesNoSelectDialogModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.YesNoDialogModel(params));


  self.options = params.options;
  self.optionsText = params.optionsText;
  self.optionsValue = params.optionsValue;
  self.value = params.value;
  self.optionsCaption = params.optionsCaption || loc("choose");
  self.valueAllowUnset = params.valueAllowUnset;
};
