LUPAPISTE.PasswordFieldModel = function(params) {
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

  self.quality = params.quality || ko.observable("");

  self.qualityInfo = ko.computed(function() {
    return loc(["mypage.quality", self.quality()]);
  });

  self.qualityStyle = ko.computed(function() {
    return self.quality();
  });

};
