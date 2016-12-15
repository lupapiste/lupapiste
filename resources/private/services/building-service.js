// Service for fetching and sharing building information.
LUPAPISTE.BuildingService = function() {
  "use strict";
  var self = this;

  var buildings = ko.observableArray();

  var latestPropertyId = null;

  function appId() {
    return lupapisteApp.services.contextService.applicationId();
  }

  function updateNeeded() {
    if( appId() ) {
      var propertyId = lupapisteApp.models.application.propertyId();
      if( propertyId !== latestPropertyId ) {
        latestPropertyId = propertyId;
        return true;
      }
    } else {
      // We are not on an application page.
      latestPropertyId = null;
    }
  }

  // Shared computed (with every DocgenBuildingSelectModel) that
  // provides a "read-only" view of the fetched building information.
  var infoView = ko.computed( function() {
    return _.clone( buildings() );
  });

  self.buildingsInfo = function() {
    if( updateNeeded() && lupapisteApp.models.applicationAuthModel.ok("get-building-info-from-wfs")) {
      ajax.query( "get-building-info-from-wfs",
                  {id: appId()})
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
  // buildingId: Building id[[/Users/vespesa/projects/lupapiste/.gitignore][]]
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
                             {id: appId(),
                              path: "buildingId",
                              collection: "documents"}))
     .success( _.partial( repository.load, appId(), _.noop))
      .onError("error.no-legacy-available", notify.ajaxError)
      .call();
  });
};
