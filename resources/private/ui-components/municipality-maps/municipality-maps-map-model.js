LUPAPISTE.MunicipalityMapsMapModel = function( params ) {
  "use strict";
  var self = this;

  var PROJECTION = "EPSG:3067";
  var BACKGROUND = {url: "/proxy/wmts/maasto",
                    layer: "taustakartta",
                    // If the layers do not have extent information
                    // the map is centered on the fountain in Tampere
                    // Keskustori
                    fountain: [327565.58, 6822550.01]};
  var backgroundSource = ko.observable();

  var PROXY        = params.PROXY;
  var capabilities = params.capabilities;
  var serverLayers = params.serverLayers;
  var mapFitted    = params.mapFitted;
  var userLayers   = params.userLayers;


  $.get( BACKGROUND.url,
       {request: "GetCapabilities",
       service: "wmts",
       layer: BACKGROUND.layer},
       function( data ) {
         var parser = new ol.format.WMTSCapabilities();
         var caps = parser.read( data );

         var options = ol.source.WMTS.optionsFromCapabilities( caps,
                                                               {layer: BACKGROUND.layer});
         options.urls = [BACKGROUND.url];
         backgroundSource( new ol.source.WMTS( options) );
       });

  function findLayerCapabilities( layerId ) {
    var layer = _.find( serverLayers(), {name: layerId});
    return layer ? layer.capabilities : null;
  }

  function findLayerExtent( layerId ) {
    var caps = findLayerCapabilities( layerId );
    if( caps ) {
      var box = _.find( caps.BoundingBox, {crs: PROJECTION});
      return util.getIn(box, ["extent", 0]) ? box.extent : null;
    }
  }

  // config from http://epsg.io/3067
  proj4.defs( PROJECTION,"+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs");
  ol.proj.get(PROJECTION).setExtent( [50199.4814, 6582464.0358, 761274.6247, 7799839.8902]);

  var map = new ol.Map( {
    target: "municipality-maps-preview",
    view: new ol.View( {
      projection: ol.proj.get( PROJECTION),
      zoom: 4,
      center: BACKGROUND.fountain
    })
  });

  ko.computed( function() {
    map.getLayers().clear();
    var caps = capabilities();
    if( backgroundSource() ) {
      map.addLayer( new ol.layer.Tile({
        visible: params.backgroundVisible(),
        type: "base",
        id: BACKGROUND.layer,
        source: backgroundSource()
      }));
    }

    if( caps && serverLayers()) {
      var extent = null;
      _.each( userLayers(), function( layer ) {
        if( layer.id()) {
          map.addLayer( new ol.layer.Tile( {
            id: layer.id(),
            visible: layer.preview(),
            source: new ol.source.TileWMS( {
              url: PROXY,
              params: {
                LAYERS: layer.id(),
                TILED: true,
                VERSION: caps.version
              }
            })
          }));
          if( !mapFitted() ) {
            var ext = findLayerExtent( layer.id());
            if(ext) {
              extent = _.clone( ext );
            }
          }
        }
      });
      if( !mapFitted() && extent ) {
        mapFitted(true);
        map.getView().fit( extent, map.getSize());
      }
    }
  });

  var hubsub = hub.subscribe("page-load", function() {
    if (map) {
      map.updateSize();
    }
  });

  self.dispose = function() {
    hub.unsubscribe(hubsub);
    if (map) {
      map.destroy();
    }
  };
};
