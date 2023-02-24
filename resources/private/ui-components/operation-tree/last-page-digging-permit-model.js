// Operation tree "last page" when creating a digging permit from application.
// This is is more a like a template than an actual, independent component.
LUPAPISTE.LastPageDiggingPermitModel = function( params ) {
  "use strict";
  var self = this;

  _.merge( self, _.pick( params, ["operation", "createDiggingPermit", "processing"]));

};
