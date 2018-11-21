LUPAPISTE.DocgenTextlinkModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-textlink-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  self.text = loc( self.schema.text );
  self.url = self.schema.url && loc( self.schema.url );
  self.icon = self.schema.icon;
};
