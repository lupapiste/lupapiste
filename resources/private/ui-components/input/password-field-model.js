LUPAPISTE.PasswordFieldModel = function(params) {
  "use strict";

  params = params || {};

  var self = this;

  // Construct super
  ko.utils.extend(self, new LUPAPISTE.InputFieldModel(params));

  self.showPassword = params.showPassword || ko.observable(false);
  self.autocomplete = params.autocomplete || ko.observable("current-password");

  self.quality = params.quality || ko.observable("");

  self.qualityInfo = self.disposedPureComputed(function() {
    return loc(["mypage.quality", self.quality()]);
  });

  self.inputClasses = self.disposedPureComputed( function() {
    return util.nonBlankJoin( [ko.unwrap( self.extraClass), self.quality()],
                              " ");
  });

};
