LUPAPISTE.DocgenAlluMapModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-allu-map-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));
};
