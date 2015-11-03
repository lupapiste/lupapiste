
LUPAPISTE.MunicipalityMapsModel = function() {
  "use strict";
  var self = this;
  var service = new LUPAPISTE.MunicipalityMapsService();
  // var serviceParams = lupapisteApp.services.municipalityMapsService.parameters;
  // self.serverParams = serviceParams.server;

  self.serverParams = service.parameters.server;
  self.layersParams = service.parameters.layers;




  // function Layer( opts) {
  //   opts = _.defaults(opts || {}, {name: "", id: "", preview: true,
  //                                 fixed: false});
  //   this.name = ko.observable( opts.name );
  //   this.id = ko.observable( opts.id );
  //   this.preview = ko.observable( opts.preview );
  //   this.fixed = opts.fixed;
  // }

  // self.url = ko.observable("");
  // self.username = ko.observable("");
  // self.password = ko.observable("");
  // self.userLayers = ko.observable([]);
  // self.error = ko.observable();

  // self.waiting = ko.observable( false );
  // self.capabilities = ko.observable();

  // // Combines (only) the named layers into an array.
  // function namedLayers( layer, arr ) {
  //   arr = arr || [];
  //   if( layer && layer.Name ) {
  //     arr.push( {name: layer.Name,
  //               title: layer.Title,
  //               capabilities: layer});
  //   }
  //   if( layer.Layer ) {
  //    _.each( _.flatten( [layer.Layer]),
  //           function( item ) {
  //             namedLayers( item, arr );
  //           });
  //   }
  //   return arr;
  // }

  // function fetchCapabilities() {
  //   $.get( PROXY,
  //          {request: "GetCapabilities",
  //           service: "wms"},
  //          function( data) {
  //            self.error( false );
  //            var parser = new ol.format.WMSCapabilities();

  //            self.capabilities(parser.read(data));
  //            console.log( "Capabilities:", self.capabilities() );
  //          },
  //        "text")
  //   .fail ( function( res ) {
  //     self.error( true );
  //     console.log( res );
  //   });
  // }


  // self.serverLayers = ko.pureComputed( function() {
  //   if( self.capabilities()) {
  //     return namedLayers (self.capabilities().Capability.Layer);
  //   }
  // });

  // function findLayerCapabilities( layerId ) {
  //   var layer = _.find( self.serverLayers(), {name: layerId});
  //   return layer ? layer.capabilities : null;
  // }

  // function findLayerExtent( layerId ) {
  //   var caps = findLayerCapabilities( layerId );
  //   if( caps ) {
  //     var box = _.find( caps.BoundingBox, {crs: PROJECTION });
  //     return box ? box.extent : null;
  //   }
  // }







  // Bootstrapping from server
  // fetchLayersData();
  // fetchCapabilities();



  // // config from http://epsg.io/3067
  // proj4.defs( PROJECTION,"+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs");
  // ol.proj.get(PROJECTION).setExtent( [50199.4814, 6582464.0358, 761274.6247, 7799839.8902]);

  // var map = new ol.Map( {
  //   target: "municipality-maps-preview",
  //   view: new ol.View( {
  //     projection: ol.proj.get( PROJECTION),
  //     zoom: 4
  //   })
  // });

  //var mapFitted = false;

  // var updateMap = ko.computed( function() {
  //   console.log( "updateMap");
  //   var caps = self.capabilities();
  //   if( caps ) {
  //     var extent = null;
  //     map.getLayers().clear();
  //     _.each( self.userLayers(), function( layer ) {
  //       if( layer.id()) {
  //         map.addLayer( new ol.layer.Tile( {
  //           id: layer.id(),
  //           visible: layer.preview(),
  //           source: new ol.source.TileWMS( {
  //             url: PROXY,
  //             params: {
  //               LAYERS: layer.id(),
  //               TILED: true,
  //               VERSION: caps.version
  //             }
  //           })
  //         }));
  //         if( !mapFitted ) {
  //           var ext = findLayerExtent( layer.id());
  //           if(ext) {
  //             extent = _.clone( ext );
  //           }
  //         }
  //       }
  //     });
  //     if( !mapFitted && extent ) {
  //       mapFitted = true;
  //       map.getView().fit( extent, map.getSize());
  //       // map.getView().setCenter(extent[0] + ((extent[2] - extent[0]) / 2),
  //       //                         extent[1] + ((extent[3] - extent[1]) / 2));
  //     }
  //   }
  // });

  // function toggleLayer( layerIdObs, visible ) {
  //   if( map ) {
  //     map.getLayers().forEach( function( layer ) {
  //       if( layer.get( "id" ) === layerIdObs() ) {
  //         layer.setVisible( visible );
  //       }
  //     });
  //   }
  // }

};
