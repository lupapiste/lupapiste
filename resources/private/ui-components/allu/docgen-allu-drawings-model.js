LUPAPISTE.DocgenAlluDrawingsModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-allu-drawings-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));
};
