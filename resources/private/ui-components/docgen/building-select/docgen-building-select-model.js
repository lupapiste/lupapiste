LUPAPISTE.DocgenBuildingSelectModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  // self.otherId = _.sprintf( "%s-%s",
  //                           params.documentId,
  //                           _.get( params, "schema.other-key", "")
  //                           .replace( /\./g, "-" ));

  self.buildingOptions = ko.observableArray();

  var textTemplate = _.template("<%- buildingId %> (<%- usage %>) - <%- created %>");

  self.optionsText = function( building ) {
    return building.buildingId === "other"
      ? loc( "select-other")
      : textTemplate( building );
  };

  hub.subscribe( "contextService::enter",
                 function( data ) {
                   ajax.query( "get-building-info-from-wfs",
                               {id: data.applicationId})
                     .success( function( res ) {
                       self.buildingOptions( _.concat( res.data, [{buildingId: "other"}]));
                     })
                     .call();
                 }
               );

    self.otherParams = _.merge( {documentId: params.documentId},
                              _.pick( lupapisteApp.services.documentDataService
                                      .getInDocument( params.documentId,
                                                      [params.schema["other-key"]]),
                                      ["path", "schema"]) );
  self.otherSelected = self.disposedPureComputed( function() {
    return ko.unwrap( self.value ) === "other";
  });
};
