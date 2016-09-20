LUPAPISTE.ExtensionApplicationsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.extensions = ko.observableArray();

  var latestAppId = null;

  function hasAuth() {
    return lupapisteApp.models.applicationAuthModel.ok( "ya-extensions");
  }

  function parseDate( date ) {
    var m = moment( date, "DD.MM.YYYY", true);
    return m.isValid() ? m : null;
  }

  function fetchExtensions() {
    self.extensions([]);
    if( hasAuth() ) {
      ajax.query( "ya-extensions", {id: latestAppId})
        .success( function( res ) {
          self.extensions(_( res.extensions )
                          .map( function( ext ) {
                            return {startDate: parseDate( ext.startDate),
                                    endDate: parseDate( ext.endDate),
                                    url: pageutil.buildPageHash( "application",
                                                                 ext.id),
                                    state: ext.state};
                          })
                          .sortBy( "startDate")
                          .value());
        })
        .call();
    }
  }

  self.disposedComputed( function() {
    var appId = lupapisteApp.models.application.id();
    if( appId
        && appId !== latestAppId
        && pageutil.hashApplicationId() === appId ) {
      latestAppId = appId;
      fetchExtensions();
    }
  });
};
