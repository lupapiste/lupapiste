// Somewhat generic location (x, y) editor. Used within the modal dialog context.
// Parameters [optional]:
//  [x]: X coordinate in EPSG:3067
//  [y]: Y coordinate in EPSG:3067
//  center: (object with x and y propertis). Default center for the
//  map, if the location is not given.
//  saveFn: Function( x, y) that is called when Save button is
//  pressed.
//  cancelFn: Function that is called Cancel button is pressed.
//
// Note: if the editor is within a dialog, the functions must
//  close the dialog as well (`hub.send("close-dialog")`).
//
// In addition the numeric checks, the coordinate validation includes
// sanity checks (coordinates must be within Finland)
LUPAPISTE.LocationEditorModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.x = ko.observable( params.x );
  self.y = ko.observable( params.y );
  var center = params.center;
  var callbackFn = params.saveFn;
  self.cancelFn = params.cancelFn;


  // Valid coordinate ranges are taken from 'sade/coordinate.clj'.

  self.validX = self.disposedComputed( function() {
    var x = util.parseFloat( self.x() );
    return x > 10000 && x < 800000 ? x : null;
  });

  self.validY = self.disposedComputed( function() {
    var y = util.parseFloat( self.y() );
    return y >= 6610000 && y <= 7779999 ? y : null;
  });

  self.warningX = self.disposedComputed( function() {
    return !_.isBlank( self.x() ) && !self.validX();
  });

  self.warningY = self.disposedComputed( function() {
    return !_.isBlank( self.y() ) && !self.validY();
  });

  self.saveFn = function() {
    callbackFn( self.validX(), self.validY());
  };

  // Map functionality

  var map = null;

  function mapVisible() {
    return $( "#location-editor-map:visible").length > 0;
  }

  function setPoint( x, y) {
    self.x( x );
    self.y( y );
  }

  function updateMarker() {
    var x = self.validX();
    var y = self.validY();
    if( map && x && y) {
      map.clear().add( {x: x,
                        y: y,
                        isTarget: true},
                     true );
    }
  }

  self.disposedComputed( function() {
    updateMarker();
  });

  function initMap() {
    var x = self.validX() || center.x;
    var y = self.validY() || center.y;
    map = gis.makeMap( "location-editor-map", {zoomWheelEnabled: false} );
    map.updateSize().center( x, y, 14 );
    map.addClickHandler( setPoint );
    updateMarker();
    self.addToDisposeQueue( {dispose: function() {
      map.clear();
    }});
  }

  var polling = ko.observable( 1 );

  // Create map when the the map div is visible.
  self.disposedComputed( function() {

    if( !map && polling()) {
      if( mapVisible() ) {
        polling( false );
        initMap();
      } else {
        _.delay( polling, 100, polling() + 1);
      }
    }
  });

};
