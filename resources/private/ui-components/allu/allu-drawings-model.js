// Allu fixed locations vs. application drawings.
// Parameters [optional]:
//
// [kind]: Location type / application kind. If not given every
// available location is listed.
// [mapId]: Corresponding map.
// [selectedType] Either custom or fixed observable. If given only the
// corresponding drawings are shown.
//
// Also disable and enable as defined in EnableComponentModel.
LUPAPISTE.AlluDrawingsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  var kind = ko.unwrap( params.kind );
  var appId = lupapisteApp.services.contextService.applicationId;

  function drawingTitle(drawing) {
    return drawing["allu-section"]
         ? drawing.name + " (" + loc("document.allu-section") + " " + drawing["allu-section"] + ")"
         : drawing.name;
  }

  self.selectedType = params.selectedType || ko.observable();
  self.deleteFn = null;
  self.selectFn = null;
  self.confirmationDeleteFn = null;

  self.authOk = self.disposedPureComputed( function() {
    return !self.isDisabled()
      && lupapisteApp.models.applicationAuthModel.ok( "allu-sites");
  });

  var drawings = self.disposedComputed( function() {
    return _.map(lupapisteApp.models.application.drawings(),
                 function(koDrawing) {
                   var drawing = ko.mapping.toJS(koDrawing);
                   return _.extend({}, _.pick(drawing, ["id", "source", "allu-id"]), {name: drawingTitle(drawing)});
                 });
  });

  self.alluDrawings = self.disposedComputed( function() {
    return _.filter( drawings(),
                     kind
                     ? {source: kind}
                     : function (drawing) { return drawing["allu-id"]; });
  });

  self.userDrawings = self.disposedComputed( function() {
    return _.reject( drawings(), "source");
  });

  var rawSites = ko.observableArray();

  self.sites = self.disposedComputed( function() {
    return _.map( rawSites(),
                  function( site ) {
                    return {text: drawingTitle(site),
                            value: site.id,
                            lGroup: "allu.application-kind." + site.source };
                  });
  });

  function fetchSites() {
    if( self.authOk() ) {
      ajax.query( "allu-sites", {id: appId(),
                                 kind: kind})
        .success( function( res ) {
          rawSites( res.sites );
        })
        .call();
    }
  }

  function successFn() {
    lupapisteApp.models.application.lightReload();
    self.selectedSite( null );
  }

  function confirmation( fun, draw ) {
    hub.send( "show-dialog", {ltitle: "areyousure",
                              size: "medium",
                              component: "yes-no-dialog",
                              componentParams: {text: loc( "allu.remove-drawing-confirmation",
                                                           draw.name),
                                                yesFn: _.wrap( draw, fun) }});
  }

  // We initialize observables within computed just in case auth model
  // changes.
  self.disposedComputed( function() {
    if( self.authOk()) {
      self.deleteFn = function( draw ) {
        ajax.command( "remove-application-drawing", {id: appId(),
                                                     drawingId: draw.id})
          .success( successFn )
          .call();
      };

      self.selectedSite = ko.observable();

      self.selectFn = function() {
        var siteId = self.selectedSite();
        ajax.command( "add-allu-drawing", {id: appId(),
                                           kind: _.find( rawSites(), {id: siteId}).source,
                                           siteId: siteId})
          .success( successFn )
          .call();
      };
    }
    else {
      self.deleteFn = null;
      self.selectFn = null;
    }

    self.confirmationDeleteFn = self.deleteFn ? _.wrap( self.deleteFn, confirmation) : null;
  });

  self.addHubListener( "application-model-updated", fetchSites );

  self.locateFn = params.mapId ? function( draw ) {
    hub.send( "allu-map-locate", {drawingId: draw.id,
                                  mapId: params.mapId});
  }
  : null;

  // Initialization

  if( kind && self.authOk() ) {
    ajax.command( "filter-allu-drawings", {id: appId(),
                                           kind: kind})
      .success( function( res ) {
        if( res.text !== "no changes") {
          successFn();
        }
      })
      .call();
  }
  fetchSites();

};
