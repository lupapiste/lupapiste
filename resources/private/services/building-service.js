// Service for fetching and sharing building information.
LUPAPISTE.BuildingService = function() {
  "use strict";
  var self = this;

  var buildings = ko.observableArray();

  var latestAppId = null;

  function updateNeeded() {
    var appId = lupapisteApp.services.contextService.applicationId();
    if( appId && appId !== latestAppId ) {
      latestAppId = appId;
      return true;
    }
  }

  // Shared computed (with every DocgenBuildingSelectModel) that
  // provides a "read-only" view of the fetched building information.
  var infoView = ko.computed( function() {
    return _.clone( buildings() );
  });

  self.buildingsInfo = function() {
    if( updateNeeded()) {
      ajax.query( "get-building-info-from-wfs",
                  {id: latestAppId})
        .success( function( res ) {
          buildings( res.data );
        })
        .call();
    }
    // Returns immediately with the view observable.
    return infoView;
  };

  // Options [optional]
  // documentId: Document id
  // buildingId: Building id
  // overwrite: Whether overwrite during merge
  // [path]: Schema path (default "buildingId")
  // [collection]: Mongo collection ("documents")
 hub.subscribe( "buildingService::merge", function( options ) {
    ajax.command("merge-details-from-krysp",
                 _.defaults( _.pick( options, ["documentId",
                                            "buildingId",
                                            "overwrite",
                                            "path",
                                            "collection"]),
                           {id: latestAppId,
                            path: "buildingId",
                            collection: "documents"}))
      .success( _.partial( repository.load, latestAppId, _.noop))
      .onError("error.no-legacy-available", notify.ajaxError)
      .call();
  });
};
