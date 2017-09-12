// Params [optional]
// yesFn Function that gets notes value as a param.
// [notes] initial notes.
LUPAPISTE.MarkReviewFaultyDialogModel = function (params) {
  "use strict";
  var self = this;

  self.notes = ko.observable( params.notes );

  self.yes = function() {
    params.yesFn( self.notes());
  };

  self.no = params.noFn || _.noop;

};
