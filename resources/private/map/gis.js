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

  var defaultIcon = makeIcon("/img/marker-blue.png", 21, 25);

  function Map(element) {
    var self = this;

    self.map = new OpenLayers.Map(element, {
      theme: "/theme/default/style.css",
      projection: new OpenLayers.Projection("EPSG:3067"),
      units: "m",
      maxExtent: new OpenLayers.Bounds(0,0,10000000,10000000),
      resolutions : [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25],
      controls: [ new OpenLayers.Control.Zoom(),         
                  new OpenLayers.Control.Navigation({ zoomWheelEnabled: false }) ]
    });

    var wmsServers = ["/proxy/nls"];
    var base = new OpenLayers.Layer("", {displayInLayerSwitcher: false, isBaseLayer: true});
    var taustakartta_5k = new OpenLayers.Layer.WMS("taustakartta_5k", wmsServers, {layers: "taustakartta_5k", format: "image/png"}, {isBaseLayer: false, maxScale: 1, minScale: 5000});
    var taustakartta_10k = new OpenLayers.Layer.WMS("taustakartta_10k", wmsServers, {layers: "taustakartta_10k", format: "image/png"}, {isBaseLayer: false, maxScale: 5001, minScale: 20000});
    var taustakartta_20k = new OpenLayers.Layer.WMS("taustakartta_20k", wmsServers, {layers: "taustakartta_20k", format: "image/png"}, {isBaseLayer: false, maxScale: 20001, minScale: 54000});
    var taustakartta_40k = new OpenLayers.Layer.WMS("taustakartta_40k", wmsServers, {layers: "taustakartta_40k", format: "image/png"}, {isBaseLayer: false, maxScale: 54001, minScale: 133000});
    var taustakartta_160k = new OpenLayers.Layer.WMS("taustakartta_160k", wmsServers, {layers: "taustakartta_160k", format: "image/png"}, {isBaseLayer: false, maxScale: 133001, minScale: 250000});
    var taustakartta_320k = new OpenLayers.Layer.WMS("taustakartta_320k", wmsServers, {layers: "taustakartta_320k", format: "image/png"}, {isBaseLayer: false, maxScale: 250001, minScale: 350000});
    var taustakartta_800k = new OpenLayers.Layer.WMS("taustakartta_800k", wmsServers, {layers: "taustakartta_800k", format: "image/png"}, {isBaseLayer: false, maxScale: 350001, minScale: 800000});
    var taustakartta_2m = new OpenLayers.Layer.WMS("taustakartta_2m", wmsServers, {layers: "taustakartta_2m", format: "image/png"}, {isBaseLayer: false, maxScale: 800001, minScale: 2000000});
    var taustakartta_4m = new OpenLayers.Layer.WMS("taustakartta_4m", wmsServers, {layers: "taustakartta_4m", format: "image/png"}, {isBaseLayer: false, maxScale: 2000001, minScale: 4000000});
    var taustakartta_8m = new OpenLayers.Layer.WMS("taustakartta_8m", wmsServers, {layers: "taustakartta_8m", format: "image/png"}, {isBaseLayer: false, maxScale: 4000001, minScale: 1.5E7});
    var kiinteistorajat = new OpenLayers.Layer.WMS("kiinteistorajat", wmsServers, {layers: "ktj_kiinteistorajat", format: "image/png", transparent: true}, {isBaseLayer: false, maxScale: 1, minScale: 20000});
    var kiinteistotunnukset = new OpenLayers.Layer.WMS("kiinteistotunnukset", wmsServers, {layers: "ktj_kiinteistotunnukset", format: "image/png", transparent: true}, {isBaseLayer: false, maxScale: 1, minScale: 10000});

    self.map.addLayers([base, taustakartta_5k, taustakartta_10k, taustakartta_20k, taustakartta_40k, taustakartta_160k, taustakartta_320k, taustakartta_800k, taustakartta_2m, taustakartta_4m, taustakartta_8m, kiinteistorajat, kiinteistotunnukset]);

    self.markerLayer = new OpenLayers.Layer.Markers("Markers");
    self.map.addLayer(self.markerLayer);

    self.markers = [];

    self.clear = function() {
      _.each(self.markers, function(marker) {
        self.markerLayer.removeMarker(marker);
        marker.destroy();
      });
      self.markers = [];
      return self;
    };

    self.add = function(x, y, markerIcon) {
      var icon = markerIcon || defaultIcon;
      var marker = makeMarker(new OpenLayers.LonLat(x, y), icon);
      self.markerLayer.addMarker(marker);
      self.markers.push(marker);
      return self;
    };

    self.center = function(x, y, zoom) {
      self.map.setCenter(new OpenLayers.LonLat(x, y), zoom);
      return self;
    };

    self.getZoom = function() {
      return self.map.zoom;
    }

    self.getMaxZoom = function() {
      return 11;
    }

    self.centerWithMaxZoom = function(x, y) {
      return self.center(x, y, self.getMaxZoom());
    }

    self.updateSize = function() {
      self.map.updateSize();
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

        initialize: function(options) {
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
    makeMap: function(element) { return new Map(element); }
  };

})();
