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
};
