// Operation tree "last page" when adding operation.
// This is is more a like a template than an actual, independent component.
LUPAPISTE.LastPageAddOperationModel = function( params ) {
  "use strict";
  var self = this;

  _.merge( self, _.pick( params, ["operation", "addOperation", "processing"]));

};
