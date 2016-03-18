LUPAPISTE.VerdictAppealBubbleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.params = params;

  self.date = ko.observable();
};
