
LUPAPISTE.MunicipalityMapsMapModel = function( params ) {
  "use strict";

  var PROJECTION = "EPSG:3067";
  var PROXY        = params.PROXY;
  var capabilities = params.capabilities;
  var serverLayers = params.serverLayers;
  var mapFitted    = params.mapFitted;
  var userLayers   = params.userLayers;

  function findLayerCapabilities( layerId ) {
    var layer = _.find( serverLayers(), {name: layerId});
    return layer ? layer.capabilities : null;
  }

  function findLayerExtent( layerId ) {
    var caps = findLayerCapabilities( layerId );
    if( caps ) {
      var box = _.find( caps.BoundingBox, {crs: PROJECTION });
      return box ? box.extent : null;
    }
  }

  // config from http://epsg.io/3067
  proj4.defs( PROJECTION,"+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs");
  ol.proj.get(PROJECTION).setExtent( [50199.4814, 6582464.0358, 761274.6247, 7799839.8902]);

  var map = new ol.Map( {
    target: "municipality-maps-preview",
    view: new ol.View( {
      projection: ol.proj.get( PROJECTION),
      zoom: 4
    })
  });

  ko.computed( function() {
    map.getLayers().clear();
    var caps = capabilities();
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

};
