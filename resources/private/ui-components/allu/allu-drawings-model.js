// Allu fixed locations vs. application drawings.
// Parameters [optional]:
// kind: Location type (promotion)
// [mapId]: Corresponding map.
//
// Also disable and enable as defined in EnableComponentModel.
LUPAPISTE.AlluDrawingsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  var kind = params.kind;
  var appId = lupapisteApp.services.contextService.applicationId;

  self.authOk = self.disposedPureComputed( function() {
    return !self.isDisabled()
      && lupapisteApp.models.applicationAuthModel.ok( "allu-sites");
  });

  var drawings = self.disposedComputed( function() {
    return _.map(lupapisteApp.models.application.drawings(),
                 function( draw ) {
                   return ko.mapping.toJS( _.pick( draw, ["name", "id", "source", "allu-id"]) );
                 });
  });

  self.alluDrawings = self.disposedComputed( function() {
    return _.filter( drawings(), {source: kind});
  });

  self.userDrawings = self.disposedComputed( function() {
    return _.reject( drawings(), "source");
  });

  self.sites = ko.observableArray();

  function fetchSites() {
    if( self.authOk() ) {
      ajax.query( "allu-sites", {id: appId(),
                                 kind: kind})
        .success( function( res ) {
          self.sites( res.sites );
        })
        .call();
    }
  }

  function successFn() {
    lupapisteApp.models.application.lightReload();
  }

  function confirmation( fun, draw ) {
    hub.send( "show-dialog", {ltitle: "areyousure",
                              size: "medium",
                              component: "yes-no-dialog",
                              componentParams: {text: loc( "allu.remove-drawing-confirmation",
                                                           draw.name),
                                                yesFn: _.wrap( draw, fun) }});
  }

  if( self.authOk()) {
    self.deleteFn = function( draw ) {
      ajax.command( "remove-application-drawing", {id: appId(),
                                                   drawingId: draw.id})
        .success( successFn )
      .call();
    };

    self.selectedSite = ko.observable();

    self.selectFn = function() {
      ajax.command( "add-allu-drawing", {id: appId(),
                                         kind: kind,
                                         siteId: self.selectedSite().id})
        .success( successFn )
        .call();
    };
  } else {
    self.deleteFn = null;
    self.selectFn = null;
  }

  self.confirmationDeleteFn = self.deleteFn ? _.wrap( self.deleteFn, confirmation) : null;

  self.addHubListener( "application-model-updated", fetchSites );

  self.locateFn = params.mapId ? function( draw ) {
    hub.send( "allu-map-locate", {drawingId: draw.id });
  }
  : null;

  // Initialization
  fetchSites();

};
