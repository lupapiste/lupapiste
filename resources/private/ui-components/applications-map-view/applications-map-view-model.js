// Show search result applications on a map.
// Parameters:
//   dataProvider: Data provider for search.
//   viewMode: The main view context. The value is either "application" or "foreman".
LUPAPISTE.ApplicationsMapViewModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var dataProvider = params.dataProvider;
  self.viewMode = params.viewMode;
  // Fountain in Keskustori, Tampere
  var fountain = {x: 327565.58, y: 6822550.01};
  var PIN = "map-marker-orange.png";
  var centerXY = ko.observable();
  var popupId = "applications-map-view-popup";
  var map = null;
  var popupModel = new LUPAPISTE.ApplicationsMapMarkerPopupModel();
  // Not very reliable, only used for focus management.
  var popupVisible = ko.observable();
  var markerInfos = ko.observableArray();

  self.rendered = ko.observable( false );
  self.applicationType = ko.observable(dataProvider.searchFields().applicationType);
  self.searching = ko.observable( false );
  self.hasFocus = ko.observable( false );

  self.selectedStates = dataProvider.selectedStates;

  var zoom = _.debounce( function( event ) {
    var delta = _.get( event, "originalEvent.wheelDeltaY", 0 );
    if( delta < 0 ) {
      map.map.zoomIn();
    }
    if( delta > 0 ) {
      map.map.zoomOut();
    }
  }, 20 );

  self.wheeling = function( data, event ) {
    if( map && self.hasFocus() && !map.selectedFeature ) {
      zoom( event );
    } else {
      return true;
    }
  };

  self.resultText = self.disposedPureComputed( function() {
    var args = null;
    var locFn = _.spread( loc );

    if( self.searching() ) {
       args = "map.search-result.searching";
    } else {
      var appCount = _( markerInfos()).values().flatten().size();
      switch( appCount ) {
      case 0:
        args = "map.search-result.none";
        break;
      case 1:
        args = "map.search-result.one";
        break;
      default:
        var n = _.size( markerInfos() );
        if( n === 1 ) {
          args = ["map.search-result.many.one-location", appCount];
        } else {
          if( n === appCount ) {
            args = ["map.search-result.many", appCount];
          } else {
            args = ["map.search-result.many.many-locations", n, appCount];
          }
        }
      }
    }
    return locFn( _.flatten( [args]));
  });

  function createPopup( feature, contents, closeFn ) {
    var popup = new OpenLayers.Popup( popupId,
                                      feature.geometry.getBounds().getCenterLonLat(),
                                      null,
                                      contents,
                                      null,
                                      true,
                                      closeFn );
    popup.panMapIfOutOfView = true;
    popup.maxSize = new OpenLayers.Size(300, 400);
    popupVisible( true );
    return popup;
  }

  function finalizePopup( popup ) {
    popupModel.bindPopup( popup );
  }

  function markerClicked( groupIds ) {
    var infos = _( _.pick( markerInfos(), groupIds ))
        .values()
        .flatten()
        .compact().
        value();
    popupModel.updateInfos( infos );
  }

  function swapFocus() {
    self.hasFocus( !self.hasFocus() );
  }

  function closePopup() {
    if( map ) {
      map.closePopup();
      popupVisible( false );
    }
  }

  self.disposedComputed( function() {
    if( !map && self.rendered()) {
      var iconOptions = _.set( {}, PIN, {width: 25, height: 30, cluster: true});

      map = gis.makeMap( "applications-marker-map", {zoomWheelEnabled: false,
                                                     iconOptions: iconOptions});
      map.map.getLayersByName( "Kiinteistojaotus" )[0].setOpacity( 0.4 );

      map.markerLayer.addOptions( {maxResolution: map.map.getResolutionForZoom( 8 )} );

      map.addClickHandler( function() {
        if( !popupVisible() ) {
          swapFocus();
        }
        closePopup();
      });

      map.map.events.register("zoomend", this, function() {
        closePopup();
      });

      map.updateSize().center( fountain.x, fountain.y, 11 );
      map.setPopupContentModel( popupModel,
                                "#applications-map-marker-popup-template",
                                createPopup,
                                finalizePopup );
      map.setMarkerClickCallback( markerClicked );

      self.addToDisposeQueue( {dispose: function() {
        map.clear();
      }});
    }
  });

  self.disposedComputed( function() {
    var xy = centerXY();
    if( xy && map ) {
      map.center( xy.x, xy.y );
    }
  });

  self.disposedComputed( function() {
    if( markerInfos() && map ) {
      var markers = _( markerInfos())
          .map( function( markers, groupId ) {
            var multiple = _.size( markers ) > 1;
            var info = _.first( markers );
            return _.merge( _.pick( info, ["x", "y"]),
                            {iconFile: PIN,
                             contents: groupId},
                            multiple ? null : _.pick( info, ["permitType", "state"]));
          })
          .concat()
          .value();
      map.clear().add( markers, false );
    }
  });

  self.disposedComputed( function() {
    if( dataProvider.mapView() ) {
      ajax.query( "application-map-markers-search",
                  self.selectedStates()
                  ? dataProvider.searchFields()
                  : _.set( dataProvider.searchFields(),
                           "applicationType",
                           self.applicationType()))
        .pending( self.searching )
        .success( function( res ) {
          var x = _.meanBy( res.markers, "location.0" );
          var y = _.meanBy( res.markers, "location.1" );
          if( x && y ) {
            centerXY({x: x, y: y});
          }
          var infos = _(res.markers)
              .map( function( marker ) {
                return _.merge( _.pick( marker,
                                        ["state", "id", "permitType", "infoRequest"]),
                                {x: marker.location[0],
                                 y: marker.location[1]});
              })
              .groupBy( function( m ) {
                return m.x + "-" + m.y;
              })
              .value();
          markerInfos( infos );
        } )
        .call();
    }
  }).extend({deferred: true});
};
