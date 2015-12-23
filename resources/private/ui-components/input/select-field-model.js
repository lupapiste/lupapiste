LUPAPISTE.SelectFieldModel = function(params) {
  "use strict";
  params = params || {};

  var self = this;

  // Construct super
  ko.utils.extend(self, new LUPAPISTE.InputFieldModel(params));

  self.options = params.options || [];
  self.optionsValue = params.optionsValue || "";
  self.optionsText  = params.optionsText || "";
  self.optionsCaption = params.lOptionsCaption ? loc(params.lOptionsCaption) : params.optionsCaption;
};
