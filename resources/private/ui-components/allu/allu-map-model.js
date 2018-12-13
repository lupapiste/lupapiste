// Map view.
// Params:
//  id: Unique map identifier
//  pageRegex: Regex that is used to determine whether the map
//  component is (somewhat) visible and thus safe to render. The regex
//  is matched agains the current hash.
LUPAPISTE.AlluMapModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel( params ));

  self.mapId = params.id;

  self.rendered = ko.observable( false );
  self.applicationReady = ko.observable( false );
  var mapPageVisible = ko.observable();

  function checkPageVisibility() {
    mapPageVisible( params.pageRegex.test( window.location.hash ));
  }

  self.addHubListener( "page-load", checkPageVisibility );

  // Copied from map-model.js
  var drawStyle = {fillColor: "#3CB8EA", fillOpacity: 0.35,
                   strokeColor: "#0000FF", pointRadius: 6};


  var map = null;

  var location = self.disposedComputed( function() {
    var lox = lupapisteApp.models.application.location;
    var x = util.getIn( lox, ["x"]);
    var y = util.getIn( lox, ["y"]);
    return _.isNumber( x ) && _.isNumber( y ) ? {x: x, y: y} : null;
  });

  function getDrawings() {
    return ko.mapping.toJS( lupapisteApp.models.application.drawings() );
  }

  function addDrawings( drawings ) {
    if( map && drawings ) {
      map.clear().drawDrawings( drawings, {}, drawStyle );
    }
  }

  // Create map when the view has been rendered.
  self.disposedComputed( function() {
    var lox = location();
    if( !map && self.rendered() && lox && mapPageVisible() ) {
      map = gis.makeMap( self.mapId, {zoomWheelEnabled: false} );
      map.updateSize().center( lox.x, lox.y, 14 );
      addDrawings( getDrawings() );
      self.addToDisposeQueue( {dispose: function() {
        map.clear();
      }});
    }
  });

  self.disposedComputed( function() {
    addDrawings( getDrawings() );
  });

  self.addHubListener( "allu-map-locate", function( msg ) {
    if( self.mapId && self.mapId === msg.mapId ) {
      var draw = _.find( getDrawings(), {id: msg.drawingId} );
      if( draw && map ) {
        map.centerOnDrawing( draw );
      }
    }
  });

  self.openMap = function(){
    lupapisteApp.models.application.openOskariMap();
  };

  checkPageVisibility();
};
