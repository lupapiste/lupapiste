var gis = (function() {
  "use strict";

  function makeIcon(image, w, h) {
    var size = new OpenLayers.Size(w, h);
    var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);
    return new OpenLayers.Icon(image, size, offset);
  }

  function makeMarker(pos, icon) {
    return new OpenLayers.Marker(pos, icon.clone());
  }

  var defaultIcon = makeIcon("/img/map-marker.png", 21, 25);

  // Map initialization

  function Map(element, zoomWheelEnabled) {
    var self = this;

    self.map = new OpenLayers.Map(element, {
      theme: "/theme/default/style.css",
      projection: new OpenLayers.Projection("EPSG:3067"),
      units: "m",

//      maxExtent: new OpenLayers.Bounds(0,0,10000000,10000000),
//      resolutions : [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25],
//      maxExtent: new OpenLayers.Bounds(-250000,-250000,250000,250000),

      maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),
      resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],

      controls: [ new OpenLayers.Control.Zoom(),
                  new OpenLayers.Control.Navigation({ zoomWheelEnabled: zoomWheelEnabled }) ]
    });

    // Layers

    // use the old proxy server to wms
    var wmtsServer = LUPAPISTE.config.maps.proxyserver;
    if (LUPAPISTE.config.maps.proxyserver.indexOf(",") > -1) {
      wmtsServer = LUPAPISTE.config.maps.proxyserver.split(",");
    }
    var base = new OpenLayers.Layer("", {displayInLayerSwitcher: false, isBaseLayer: true});

    // VANHAT
//    var taustakartta = new OpenLayers.Layer.WMS(
//        "taustakartta",
//        wmtsServer,
//        {layers: "taustakartta", format: "image/png"},
//        {isBaseLayer: false});
//    var kiinteistorajat = new OpenLayers.Layer.WMS(
//        "kiinteistorajat",
//        wmtsServer,
//        {layers: "ktj_kiinteistorajat", format: "image/png", transparent: true},
//        {isBaseLayer: false, maxScale: 1, minScale: 20000}
//        );
//    var kiinteistotunnukset = new OpenLayers.Layer.WMS(
//        "kiinteistotunnukset",
//        wmtsServer,
//        {layers: "ktj_kiinteistotunnukset", format: "image/png", transparent: true}, //"transparent: true" ei pakollinen
//        {isBaseLayer: false, maxScale: 1, minScale: 10000});

    // UUDET
    var taustakartta = new OpenLayers.Layer.WMTS({
          name: "Taustakartta",
          url: wmtsServer,
          isBaseLayer: false,
          requestEncoding: "KVP",
          transitionEffect: "resize",
          layer: "taustakartta",
          matrixSet: "ETRS-TM35FIN",
          format: "image/png",
          style: "default",
          opacity: 1.0,

//          maxExtent: new OpenLayers.Bounds(0,0,10000000,10000000),
//          resolutions : [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25],
//          maxExtent: new OpenLayers.Bounds(-250000,-250000,250000,250000),

          resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
          maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),

          projection: new OpenLayers.Projection("EPSG:3067")
      });
    var kiinteistorajat = new OpenLayers.Layer.WMTS({

//          maxScale: 1, minScale: 20000,  // vanhasta

          name: "Kiinteistojaotus",
          url: wmtsServer,
          isBaseLayer: false,
          requestEncoding: "KVP",
//          transitionEffect: "resize",
          layer: "kiinteistojaotus",
          matrixSet: "ETRS-TM35FIN",
          format: "image/png",
          style: "default",
          opacity: 1.0,

//          maxExtent: new OpenLayers.Bounds(0,0,10000000,10000000),
//          resolutions : [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25],

//          resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
//          maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),

          resolutions: [4, 2, 1, 0.5],
          maxExtent: new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),
//          maxExtent: new OpenLayers.Bounds(-250000,-250000,250000,250000),

          projection: new OpenLayers.Projection("EPSG:3067")
      });
    var kiinteistotunnukset = new OpenLayers.Layer.WMTS({

//          maxScale: 1, minScale: 10000,  // vanhasta

          name: "Kiinteistotunnukset",
          url: wmtsServer,
          isBaseLayer: false,
          requestEncoding: "KVP",
//          transitionEffect: "resize",
          layer: "kiinteistotunnukset",
          matrixSet: "ETRS-TM35FIN",
          format: "image/png",
          style: "default",
          opacity: 1.0,

//          maxExtent: new OpenLayers.Bounds(0,0,10000000,10000000),
//          resolutions : [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25]

//          resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
//          maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),
          // TESTI
          resolutions: [4, 2, 1, 0.5],
          maxExtent: new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),
//          maxExtent: new OpenLayers.Bounds(-250000,-250000,250000,250000),

          projection: new OpenLayers.Projection("EPSG:3067")
      });
    // <- UUSI

    self.vectorLayer = new OpenLayers.Layer.Vector("Vector layer");

    if (!features.enabled("maps-disabled")) {
      self.map.addLayers([base, taustakartta, kiinteistorajat, kiinteistotunnukset, self.vectorLayer]);
    } else {
      self.map.addLayers([base, self.vectorLayer]);
    }

    // Markers

    self.markerLayer = new OpenLayers.Layer.Markers("Markers");
    self.map.addLayer(self.markerLayer);

    self.markers = [];

    self.clear = function() {
      _.each(self.markers, function(marker) {
        self.markerLayer.removeMarker(marker);
        marker.destroy();
      });
      self.markers = [];

      self.vectorLayer.removeAllFeatures();

      return self;
    };

    self.add = function(x, y, markerIcon) {
      var icon = markerIcon || defaultIcon;
      var marker = makeMarker(new OpenLayers.LonLat(x, y), icon);
      self.markerLayer.addMarker(marker);
      self.markers.push(marker);
      return self;
    };

    // Map handling functions

    self.center = function(x, y, zoom) {
      self.map.setCenter(new OpenLayers.LonLat(x, y), zoom);
      return self;
    };

    self.zoomTo = function(zoom) {
      self.map.zoomTo(zoom);
      return self;
    };

    self.getZoom = function() {
      return self.map.getZoom();
    };

    self.getMaxZoom = function() {
      return 11;
    };

    self.centerWithMaxZoom = function(x, y) {
      return self.center(x, y, self.getMaxZoom());
    };

    self.updateSize = function() {
      self.map.updateSize();
      return self;
    };

    self.drawDrawings = function(drawings, attrs, style) {
      if (drawings) {
        var addFeatureFn = function(memo, drawing) {
          var newFeature = new OpenLayers.Feature.Vector(OpenLayers.Geometry.fromWKT(drawing.geometry), attrs, style);
          memo.push(newFeature);
          return memo;
        };
        var featureArray = _.reduce(drawings, addFeatureFn, []);
        if (featureArray.length > 0) self.vectorLayer.addFeatures(featureArray);
      }
      return self;
    };

    self.addClickHandler = function(handler) {
      var ClickControl = OpenLayers.Class(OpenLayers.Control, {
        defaultHandlerOptions: {
          "single": true,
          "double": false,
          "pixelTolerance": 0,
          "stopSingle": false,
          "stopDouble": false
        },

        initialize: function() {
          this.handlerOptions = OpenLayers.Util.extend({}, this.defaultHandlerOptions);
          OpenLayers.Control.prototype.initialize.apply(this, arguments);
          this.handler = new OpenLayers.Handler.Click(this, {"click": this.trigger}, this.handlerOptions);
        },

        trigger: function(e) {
          var pos = self.map.getLonLatFromPixel(e.xy);
          handler(pos.lon, pos.lat);
        }
      });

      var click = new ClickControl();
      self.map.addControl(click);
      click.activate();

      return self;
    };
  }

  return {
    makeMap: function(element, zoomWheelEnabled) { return new Map(element, zoomWheelEnabled); }
  };

})();
