// Date editor component to use within dialog.
// Parameter:
//   date: Date observable containing a Finnish date string
//   okFn: OK click handler.
LUPAPISTE.DateEditorModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.date = params.date;
  self.okFn = params.okFn;

  self.yesEnabled = self.disposedComputed( function() {
    return self.date() && _.trim( self.date());
  });
};
