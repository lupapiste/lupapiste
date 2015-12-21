LUPAPISTE.PasswordFieldModel = function(params) {
  "use strict";

  params = params || {};

  var self = this;

  // Construct super
  ko.utils.extend(self, new LUPAPISTE.InputFieldModel(params));

  self.quality = params.quality || ko.observable("");

  self.qualityInfo = ko.computed(function() {
    return loc(["mypage.quality", self.quality()]);
  });

  self.qualityStyle = ko.computed(function() {
    return self.quality();
  });

};
