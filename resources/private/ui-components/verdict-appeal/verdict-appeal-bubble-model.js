LUPAPISTE.VerdictAppealBubbleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.params = params;
  self.authors = ko.observable();

  self.date = ko.observable( "20.1.2022");
  self.extra = ko.observable( "More information.");

  self.files = ko.observableArray();

  function getMoment() {
    return moment( _.trim(self.date()), "D.M.YYYY", true);
  }

  self.dateWarning = self.disposedComputed( function() {
    return !_.trim( self.date()) || getMoment().isValid()
      ? "" : "error.invalid-date";
  });
};
