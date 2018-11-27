// Map view.
// Params:
//  id: Unique map identifier
LUPAPISTE.AlluMapModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel( params ));

  self.mapId = params.id;

  self.rendered = ko.observable( false );
  self.applicationReady = ko.observable( false );

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

  self.disposedComputed( function() {
    var lox = location();
    if( !map && self.rendered() && lox) {
      map = gis.makeMap( self.mapId, {zoomWheelEnabled: false} );
      map.updateSize().center( lox.x, lox.y, 14 );
      addDrawings( getDrawings() );
      self.addToDisposeQueue( {dispose: function() {
        map.clear().destroy();
      }});
    }
  });

  self.disposedComputed( function() {
    addDrawings( getDrawings() );
  });

  self.addHubListener( "allu-map-locate", function( msg ) {
    var draw = _.find( getDrawings(), {id: msg.drawingId} );
    if( draw && map ) {
      map.centerOnDrawing( draw );
    }
  });

  self.openMap = function(){
    lupapisteApp.models.application.openOskariMap();
  };

};
