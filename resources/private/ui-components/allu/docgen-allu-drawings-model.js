LUPAPISTE.DocgenAlluDrawingsModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-allu-drawings-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  var service = params.service || lupapisteApp.services.documentDataService;

  self.selectedType = self.disposedComputed( function() {
    var selector = self.schema["type-select"];
    if( selector ) {
      return service.getInDocument( self.documentId, _.concat( _.dropRight( self.path ),
                                                               [selector] )).model();
    }
  });

  self.kind = self.disposedPureComputed( function() {
    var kind = self.schema.kind;
    if( kind ) {
      if( _.isString( kind )) {
        return kind;
      }
      var docId = kind.document
          ? service.findDocumentByName( kind.document ).id
          : self.documentId;
      return service.getInDocument( docId,
                                    _.split( kind.path, ".")).model();
    }
  });
};
