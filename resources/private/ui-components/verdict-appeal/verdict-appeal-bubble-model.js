// Verdict appeal creation/edit bubble.
// The function is very lightweight since the actual
// model data is passed in parameters and the UI details
// are handled by form-cells and file-upload component.
LUPAPISTE.VerdictAppealBubbleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.params = params;

  var m = params.model;

  self.model = m.model;
  self.okFun = m.okFun;
  self.initFun = m.initFun;
  self.error = m.error;
  self.waiting = m.waiting;

  self.appealTypes = _.map(["appealVerdict","appeal", "rectification"],
                           function( s ) {
                             return {text: "verdict.muutoksenhaku." + s,
                                     id: s };
                           });

  function getMoment() {
    return moment( _.trim(self.model.date()), "D.M.YYYY", true);
  }

  self.dateWarning = self.disposedComputed( function() {
    return !_.trim( self.model.date()) || getMoment().isValid()
      ? "" : "error.invalid-date";
  });

  self.okEnabled = self.disposedComputed(function() {
    var required = _.every( [self.model.appealType(),
                             self.model.authors(),
                             self.model.date()], function( v ) {
                               return _.trim( v );
                             });
    var warn = self.dateWarning() === "";
    var files = _.size(self.model.files());
    return required && warn && files;
  });
};
