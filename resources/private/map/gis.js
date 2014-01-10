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

  function Map(element, zoomWheelEnabled) {
    var self = this;

    self.map = new OpenLayers.Map(element, {
      theme: "/theme/default/style.css",
      projection: new OpenLayers.Projection("EPSG:3067"),
      units: "m",
      maxExtent: new OpenLayers.Bounds(0,0,10000000,10000000),
      resolutions : [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25],
      controls: [ new OpenLayers.Control.Zoom(),
                  new OpenLayers.Control.Navigation({ zoomWheelEnabled: zoomWheelEnabled }) ]
    });


    // use the old proxy server to wms
    var wmsServer = LUPAPISTE.config.maps.proxyserver;
    if (LUPAPISTE.config.maps.proxyserver.indexOf(",") > -1) {
      wmsServer = LUPAPISTE.config.maps.proxyserver.split(",");
    }
    var base = new OpenLayers.Layer("", {displayInLayerSwitcher: false, isBaseLayer: true});
    var taustakartta = new OpenLayers.Layer.WMS("taustakartta", wmsServer, {layers: "taustakartta", format: "image/png"}, {isBaseLayer: false});
    var kiinteistorajat = new OpenLayers.Layer.WMS("kiinteistorajat", wmsServer, {layers: "ktj_kiinteistorajat", format: "image/png", transparent: true}, {isBaseLayer: false, maxScale: 1, minScale: 20000});
    var kiinteistotunnukset = new OpenLayers.Layer.WMS("kiinteistotunnukset", wmsServer, {layers: "ktj_kiinteistotunnukset", format: "image/png", transparent: true}, {isBaseLayer: false, maxScale: 1, minScale: 10000});

    self.vectorLayer = new OpenLayers.Layer.Vector("Vector layer");

    if (!features.enabled("maps-disabled")) {
      self.map.addLayers([base, taustakartta, kiinteistorajat, kiinteistotunnukset, self.vectorLayer]);
    } else {
      self.map.addLayers([base, self.vectorLayer]);
    }

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

    self.drawShape = function(shape) {
      var vector = new OpenLayers.Feature.Vector(OpenLayers.Geometry.fromWKT(shape), {}, {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF"});
      self.vectorLayer.addFeatures([vector]);
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
