// Cell support for span.
// Span contents are determined by params
// Params [optional]:
//  value: The starting point for span contents. If no other params is
//  given, then this is the span's textual value
//  [prefix]: localization key prefix for value. Thus, the span text
//   would be loc( prefix + value).
LUPAPISTE.CellSpanModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.CellModel( params ) );

  self.text = self.disposedComputed( function() {
    var v = self.value();
    return params.prefix ? loc( params.prefix + v) : v;
  });
};
