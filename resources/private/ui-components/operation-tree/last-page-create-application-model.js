// Operation tree "last page" when creating application.
// This is is more a like a template than an actual, independent component.
LUPAPISTE.LastPageCreateApplicationModel = function( params ) {
  "use strict";
  var self = this;

  (params.lastPageInitFn || _.noop)();

  _.merge( self, _.pick( params, ["operation", "organization", "attachmentsForOp",
                                  "goPhase3", "createOK", "pending", "createApplication"]));

};
