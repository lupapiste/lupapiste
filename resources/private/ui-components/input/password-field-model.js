LUPAPISTE.PasswordFieldModel = function(params) {
  "use strict";

  params = params || {};

  var self = this;

  // Construct super
  ko.utils.extend(self, new LUPAPISTE.InputFieldModel(params));

  self.quality = params.quality || ko.observable("");

  self.qualityInfo = self.disposedPureComputed(function() {
    return loc(["mypage.quality", self.quality()]);
  });

  self.inputClasses = self.disposedPureComputed( function() {
    return util.nonBlankJoin( [ko.unwrap( self.extraClass), self.quality()],
                              " ");
  });

};
