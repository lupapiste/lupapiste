LUPAPISTE.DocgenBuildingSelectModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  var service = lupapisteApp.services.buildingService;

  self.buildingOptions = self.disposedComputed( function() {
    var infos = service.buildingsInfo();
    return _.concat( infos(), [{buildingId: "other"}]);
  });

  // Notify service that application has changed.
  self.addHubListener( "contextService::enter", service.buildingsInfo);

  var textTemplate = _.template("<%- buildingId %> (<%- usage %>) - <%- created %>");

  self.optionsText = function( building ) {
    return building.buildingId === "other"
      ? loc( "select-other")
      : textTemplate( building );
  };

  self.otherParams = _.merge( {documentId: params.documentId},
                              _.pick( lupapisteApp.services.documentDataService
                                      .getInDocument( params.documentId,
                                                      [params.schema["other-key"]]),
                                      ["path", "schema"]) );
  self.otherSelected = self.disposedPureComputed( function() {
    return ko.unwrap( self.value ) === "other";
  });
};
