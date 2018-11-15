LUPAPISTE.DocgenTimeModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-docgen-time-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

};
