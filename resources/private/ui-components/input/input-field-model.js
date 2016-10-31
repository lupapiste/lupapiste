LUPAPISTE.InputFieldModel = function(params) {
  "use strict";

  params = params || {};

  var self = this;

  self.id = params.id || util.randomElementId();
  self.name = params.name;
  self.label = params.lLabel ? loc(params.lLabel) : params.label;
  self.value = params.value;
  self.placeholder = params.lPlaceholder ? loc(params.lPlaceholder) : params.placeholder;
  self.isSelected = params.hasFocus || ko.observable();
  self.maxlength = params.maxlength || 255;

  self.required = params.required || false;
  if (self.required) {
    self.value.extend({required: true});
  }

  self.disable = params.disable || ko.observable(false);

  self.infoMsg = params.infoMsg || "";
  self.infoStyle = params.infoStyle || "";

};
