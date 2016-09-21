LUPAPISTE.ExtensionApplicationsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.extensions = ko.observableArray();

  function hasAuth() {
    return lupapisteApp.models.applicationAuthModel.ok( "ya-extensions");
  }

  function parseDate( date ) {
    var m = moment( date, "DD.MM.YYYY", true);
    return m.isValid() ? m : null;
  }

  function fetchExtensions( opts ) {
    var appId = _.get( opts, "applicationId");
    self.extensions([]);
    if( hasAuth() && appId) {
      ajax.query( "ya-extensions", {id: appId})
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

  hub.subscribe( "contextService::enter", fetchExtensions);

  // Explicit initialization, since the model may have missed the
  // original enter event.
  fetchExtensions( {applicationId:
                    lupapisteApp.services.contextService.applicationId()});
};
