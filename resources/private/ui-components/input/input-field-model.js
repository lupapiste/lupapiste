LUPAPISTE.InputFieldModel = function(params) {
  "use strict";

  params = params || {};

  var self = this;

  self.id = params.id || util.randomElementId();
  self.label = params.lLabel ? loc(params.lLabel) : params.label;
  self.value = params.value;
  self.placeholder = params.lPlaceholder ? loc(params.lPlaceholder) : params.placeholder;

  self.required = params.required || false;
  if (self.required) {
    self.value.extend({required: true});
  }

  // TODO select model
  self.options = params.options || [];
  self.optionsValue = params.optionsValue || "";
  self.optionsText  = params.optionsText || "";
  self.optionsCaption = params.lOptionsCaption ? loc(params.lOptionsCaption) : params.optionsCaption;
};
