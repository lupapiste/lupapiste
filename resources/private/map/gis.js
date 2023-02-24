var gis = (function() {
  "use strict";

  OpenLayers.ImgPath = "/lp-static/lib/openlayers2-img/";

  function iconFilePath( filename ) {
    return filename && "/lp-static/img/" + filename;
  }

  var iconDefaultPath  = iconFilePath( "map-marker-big.png" );
  var iconLocMapping = {
    "sameLocation"  : iconDefaultPath,
    "sameOperation" : iconFilePath("map-marker-green.png"),
    "others"        : iconFilePath("map-marker-orange.png"),
    "cluster"       : iconFilePath("map-marker-group.png"),
    "target"        : iconFilePath("target-marker.png")
  };

  function withSuffix(strOrArr, suffix) {
    if (_.isArray(strOrArr)) {
      return _.map(strOrArr, function(s) {
        return s + suffix;
      });
    }
    return strOrArr + suffix;
  }

  // Map initialization

  function Map(element, options) {

    if (!document.getElementById(element)) {
      error("Will not create map: #" + element + " not in DOM");
      return;
    }

    var self = this;
    var allLayers;
    var mapConfig = LUPAPISTE.config.maps;
    var openNlsMap = mapConfig["open-nls-wmts"]; // if open NLS mapserver is used (testing & hacking)

    var controls = [new OpenLayers.Control.Zoom({zoomInText:"\ue63d", zoomOutText:"\ue63e"}),
                    new OpenLayers.Control.Navigation({ zoomWheelEnabled: options && options.zoomWheelEnabled })];

    if (options && options.drawingControls) {
      self.manualDrawingLayer = new OpenLayers.Layer.Vector("Editable");
      controls.push(new OpenLayers.Control.LupapisteEditingToolbar(self.manualDrawingLayer));
    }

    var resolutions = [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1];
    if ( !openNlsMap ) {
      resolutions.push(0.5);
    }

    self.map = new OpenLayers.Map(element, {
      theme: null,
      projection: new OpenLayers.Projection("EPSG:3067"),
      units: "m",
      maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),
      resolutions : resolutions,
      controls: controls
    });

    // Layers

    // In production multiple servers, locally it's just localhost.
    var mapServer = openNlsMap || mapConfig["proxyserver-wmts"]; // open or proxy wmts
    if (mapServer.indexOf(",") > -1) {
      mapServer = mapServer.split(",");
    }
    var base = new OpenLayers.Layer("base", {displayInLayerSwitcher: false, isBaseLayer: true});

    var taustakartta = new OpenLayers.Layer.WMTS({ // this is available in open NLS map
      name: "Taustakartta",
      url: withSuffix(mapServer, "/maasto"),
      isBaseLayer: false,
      requestEncoding: "KVP",
      layer: "taustakartta",
      matrixSet: "ETRS-TM35FIN",
      format: "image/png",
      style: "default",
      opacity: 1.0,
      resolutions : resolutions,
      // maxExtent not defined here -> inherits from the config of the map
      projection: new OpenLayers.Projection("EPSG:3067")
    });

    var kiinteistorajat = new OpenLayers.Layer.WMTS({
      name: "Kiinteistojaotus",
      url: withSuffix(mapServer, "/kiinteisto"),
      isBaseLayer: false,
      requestEncoding: "KVP",
      layer: "kiinteistojaotus",
      matrixSet: "ETRS-TM35FIN",
      format: "image/png",
      style: "default",
      opacity: 1.0,
      resolutions: [4, 2, 1, 0.5],
      // maxExtent not defined here -> inherits from the config of the map
      projection: new OpenLayers.Projection("EPSG:3067")
    });

    var kiinteistotunnukset = new OpenLayers.Layer.WMTS({
      name: "Kiinteistotunnukset",
      url: withSuffix(mapServer,"/kiinteisto"),
      isBaseLayer: false,
      requestEncoding: "KVP",
      layer: "kiinteistotunnukset",
      matrixSet: "ETRS-TM35FIN",
      format: "image/png",
      style: "default",
      opacity: 1.0,
      resolutions: [4, 2, 1, 0.5],
      // maxExtent not defined here -> inherits from the config of the map
      projection: new OpenLayers.Projection("EPSG:3067")
    });


    self.vectorLayer = new OpenLayers.Layer.Vector("Vector layer");

    if (!features.enabled("maps-disabled") && !openNlsMap) {
      allLayers = [base, taustakartta, kiinteistorajat, kiinteistotunnukset, self.vectorLayer];
    } else if (!features.enabled("maps-disabled") && openNlsMap) { // open NLS (MML) WMTS uses 'taustakartta'
      allLayers = [base, taustakartta, self.vectorLayer];
    } else {
      allLayers = [base, self.vectorLayer];
    }

    if (options && options.drawingControls) {
      allLayers.push(self.manualDrawingLayer);
    }

    try {
      self.map.addLayers(allLayers);
    } catch (e) {
      // Try to catch LPK-1662 & LPK-816
      error("Unable to add layers to " + element, _.map(allLayers, "name"));
      throw e;
    }

    //
    // Hack: Did not manage to adjust the configs of the layers and the map (resolutions and maxExtent)
    //       so that the old resolutions array [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25]
    //       would work.
    //
    self.map.events.register("zoomend", self.map, function () {
      // hide marker contents div on the inforequest markers map, because marker clustering may have been divided or merged markers
      if (self.markerMapCloseCallback) {
        self.markerMapCloseCallback();
      }

      if( self.map.getZoom() < 2) {
        // For some reason, calling only "self.map.zoomTo(2);" did not work here.
        // http://gis.stackexchange.com/questions/25080/why-doesnt-openlayers-zoom
        self.map.setCenter(self.map.getCenter(), 2);
      }
    });


    // Markers

    // Info contains widht, height and cluster (true, if the icon is also used for cluster).
    function iconInfoFromOptions( feature ) {
      if (options !== undefined) {
        return _.get( options.iconOptions, feature.cluster[0].attributes.iconFile);
      }
      return null;
    }

    var getIconHeight = function(feature) {
      var info = iconInfoFromOptions( feature ) || {};

      if (feature.cluster && (feature.cluster.length > 1 || feature.cluster[0].attributes.isCluster)) {
        return info.cluster ? info.height : 53;
      }
      if( info.height ) {
        return info.height;
      }
      if( feature.cluster[0].attributes.isTarget ) {
        return 38;
      }
      if (feature.cluster[0].style.externalGraphic === iconDefaultPath) {
        return 47;
      } else {
        return 30;
      }
    };

    var context = {
      extGraphic: function(feature) {
        var info = iconInfoFromOptions( feature ) || {};
        var iconPath = null;
        if (feature.cluster) {
          if( info.cluster ) {
           iconPath = feature.cluster[0].style.externalGraphic;
          } else {
            if (feature.cluster.length > 1) {
              iconPath = iconLocMapping.cluster;
            } else {
              iconPath = feature.cluster[0].attributes.isCluster ? iconLocMapping.cluster : feature.cluster[0].style.externalGraphic;
            }
          }
        } else {
          iconPath = feature.style.externalGraphic;
        }
        return iconPath || iconDefaultPath;
      },
      graphicWidth: function(feature) {
        var info = iconInfoFromOptions( feature ) || {};

        if (feature.cluster && (feature.cluster.length > 1 || feature.cluster[0].attributes.isCluster)) {
          return info.cluster ? info.width : 56;
        }
        if( info.width ) {
          return info.width;
        }
        if( feature.cluster[0].attributes.isTarget ) {
          return 38;
        }
        if (feature.cluster[0].style.externalGraphic === iconDefaultPath) {
          return 44;
        } else {
          return 25;
        }
      },
      graphicHeight: function(feature) {
        return getIconHeight(feature);
      },
      graphicYOffset: function(feature) {
        return feature.cluster[0].attributes.isTarget ? -19 : -1 * getIconHeight(feature);
      }
    };

    var stylemap = new OpenLayers.StyleMap({
      "default": new OpenLayers.Style({
        externalGraphic: "${extGraphic}",
        graphicWidth:    "${graphicWidth}",
        graphicHeight:   "${graphicHeight}",   //alt to pointRadius
        graphicYOffset:  "${graphicYOffset}",
        cursor:          "default"
      }, {
        context: context
      }),
      "select": new OpenLayers.Style({
        // This 'select' cannot be completely removed from here, because then the markers' select functionality
        // starts to act in an unwanted way: i.e. previously selected markers first dim and then disappear
        // on the following selections etc.
//        cursor: 'pointer'
      })
    });

    var strategy = new OpenLayers.Strategy.Cluster({distance: 25/*, threshold: 2*/});

    self.markerLayer = new OpenLayers.Layer.Vector( "Markers" , {strategies: [strategy], styleMap: stylemap} );
    self.map.addLayer(self.markerLayer);

    self.markers = [];


    self.clearManualDrawings = function() {
      if (self.manualDrawingLayer) {
        self.manualDrawingLayer.removeAllFeatures();
      }
    };

    self.clear = function() {
      if (self.markerMapCloseCallback) {
        self.markerMapCloseCallback();
      }

      if (self.selectedFeature) {
        onPopupClosed(self.selectedFeature);
      }

      self.clearManualDrawings();

      self.vectorLayer.removeAllFeatures();

      self.markerLayer.removeAllFeatures();
      _.each(self.markers, function(marker) { marker.destroy(); });
      self.markers = [];

      return self;
    };

    // Select control

    var popupId = "popup-id";
    self.selectedFeature = null;
    self.popupContentModel = null;

    function clearMarkerKnockoutBindings(feature) {
      if (feature && feature.popup) {
        // Making sure Knockout's bindings are cleaned, memory is freed and handlers removed
        ko.cleanNode(feature.popup.contentDiv);
        $(feature.popup.contentDiv).empty();
        self.map.removePopup(feature.popup);
        feature.popup.destroy();
        feature.popup = null;
      }
      if (feature && feature.cluster && feature.cluster[0].popup) {
        // Making sure Knockout's bindings are cleaned, memory is freed and handlers removed
        ko.cleanNode(feature.cluster[0].popup.contentDiv);
        $(feature.cluster[0].popup.contentDiv).empty();
        self.map.removePopup(feature.cluster[0].popup);
        feature.cluster[0].popup.destroy();
        feature.cluster[0].popup = null;
      }
    }

    function onPopupClosed(feature) {
      clearMarkerKnockoutBindings(feature);
      self.selectedFeature = null;
      if (self.markerMapCloseCallback) {
        self.markerMapCloseCallback();
      }
    }

    self.closePopup = function() {
      if (self.selectedFeature) {
        // If using here "self.selectControl.unselect(self.selectedFeature);" and doing this stuff in onUnselect,
        // was getting the error message "Uncaught TypeError: Cannot read property 'drawFeature' of null".
        onPopupClosed(self.selectedFeature);
      }
    };

    function createPopup(feature, html) {
      var anchor = {
          size: new OpenLayers.Size(0,0),
          offset: new OpenLayers.Pixel(100,200)
      };
      var popup = new OpenLayers.Popup.Anchored(
          popupId,                                              // id (not used)
          feature.geometry.getBounds().getCenterLonLat(),       // lonlat
          null,                                                 // contentSize
          html,                                                 // (html content)
          anchor,                                               // anchor
          true,                                                 // closeBox
          self.closePopup);                                          // closeBoxCallback

      popup.panMapIfOutOfView = true;
      popup.relativePosition = "br";
      popup.calculateRelativePosition = function() {return "tr";};
      popup.closeOnMove = false;
      popup.autoSize = true;
      popup.minSize = new OpenLayers.Size(270, 580);
      popup.maxSize = new OpenLayers.Size(270, 580);
      return popup;
    }

    self.selectControl = new OpenLayers.Control.SelectFeature(self.markerLayer, {
      autoActivate: true,
      clickOut: true,
      toggle: true,

      onSelect: function(feature) {
        self.selectedFeature = feature;

        //
        // TODO: Could only one OpenLayers Popup instance be used here?
        //
        if (self.popupContentModel) {
          var html = $(self.popupContentModel.templateId)[0].innerHTML;
          feature.popup =  self.popupContentModel.createFn
            ? self.popupContentModel.createFn( feature, html, self.closePopup)
            : createPopup(feature, html);
          self.map.addPopup(feature.popup, true);
          $("#" + feature.popup.id + "_contentDiv").applyBindings(self.popupContentModel.model);
          if( self.popupContentModel.finalizeFn ) {
            self.popupContentModel.finalizeFn( feature.popup );
          }
        }

        if (self.markerClickCallback) {
          var contents = feature.cluster
              ? _.map( feature.cluster,
                       function(entry) {
                         return entry.data.contents;
                       })
              : [feature.data.contents];
          _.remove( contents, _.isBlank );
          self.markerClickCallback( _.isEmpty( contents ) ? null : contents,
                                  feature );
        }
      },

      onUnselect: onPopupClosed
    });

    self.addControl = function(ctrl) {
      self.map.addControl(ctrl);
      return self;
    };

    self.addControl(self.selectControl);


    // Adding markers

    self.add = function(markerInfos, autoSelect) {
      var newMarkers = [];
      markerInfos = _.isArray(markerInfos) ? markerInfos : [markerInfos];

      _.each(markerInfos, function(markerInfo) {
        var iconName = (markerInfo.isCluster && "cluster")
            || (markerInfo.isTarget && "target")
            || markerInfo.iconName;
        var iconPath = iconFilePath( markerInfo.iconFile)
            || iconLocMapping[iconName]
            || iconDefaultPath;
        var markerFeature = new OpenLayers.Feature.Vector(
          new OpenLayers.Geometry.Point(markerInfo.x, markerInfo.y),
          {isCluster: markerInfo.isCluster || false,
           isTarget: markerInfo.isTarget && !markerInfo.isCluster,
           iconFile : markerInfo.iconFile,
           contents: markerInfo.contents || "" },
          {externalGraphic: iconPath});

        self.markers.push(markerFeature);
        newMarkers.push(markerFeature);
      });  //each

      self.markerLayer.addFeatures(newMarkers);

      if (autoSelect && self.popupContentModel) {
        self.selectControl.select(self.markerLayer.features[0]);
      }

      return self;
    };

    self.setPopupContentModel = function(model, templateId, createFn, finalizeFn ) {
      self.popupContentModel = {
        model: model,
        templateId: templateId,
        createFn: createFn,
        finalizeFn: finalizeFn
      };
      return self;
    };

    self.setMarkerClickCallback = function(handler) {
      self.markerClickCallback = handler;
      return self;
    };

    self.setMarkerMapCloseCallback = function(handler) {
      self.markerMapCloseCallback = handler;
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
          var wkt = _.isString(drawing) ? drawing : drawing.geometry;
          if (wkt) {
            var newFeature = new OpenLayers.Feature.Vector(OpenLayers.Geometry.fromWKT(wkt), attrs, style);
            memo.push(newFeature);
          }
          return memo;
        };
        var featureArray = _.reduce(drawings, addFeatureFn, []);
        if (featureArray.length > 0) {
          self.vectorLayer.addFeatures(featureArray);
        }
      }
      return self;
    };

    self.centerOnDrawing = function( drawing ) {
      var wkt = _.isString(drawing) ? drawing : drawing.geometry;
      if (wkt) {
        var center = OpenLayers.Geometry.fromWKT(wkt).getCentroid();
        self.center( center.x, center.y, self.map.zoom );
      }
    };

    self.addClickHandler = function(handler) {
      var ClickControl = OpenLayers.Class(OpenLayers.Control, {
        defaultHandlerOptions: {
          "single": true,
          "double": false,
          "pixelTolerance": 10,
          "stopSingle": false,
          "stopDouble": false
        },

        initialize: function() {
          this.handlerOptions = OpenLayers.Util.extend({}, this.defaultHandlerOptions);
          OpenLayers.Control.prototype.initialize.apply(this, arguments);
          this.handler = new OpenLayers.Handler.Click(this, {"click": this.trigger}, this.handlerOptions);
        },

        trigger: function(e) {
          var event = getEvent(e);
          //
          // When marker (event.target.nodeName === "image") is clicked, let's prevent further reacting to the click here.
          // This is somewhat a hack. It would be better to find a way to somehow stop propagation of click event earlier
          // in the selectControl's onSelect callback, or by the marker item (OpenLayers.Feature.Vector) itself.
          //
          // HACK: Added check for the event.target.nodeName "DIV" to prevent creating new marker when marker popup's close cross is pressed.
          //
          if (!event.target || (event.target.nodeName !== "image" && event.target.className !== "olPopupCloseBox")) {
            var pos = self.map.getLonLatFromPixel(event.xy);
            handler(pos.lon, pos.lat);
          }
        }
      });

      self.destroy = function() {
        self.clear().map.destroy();
      };

      var click = new ClickControl();
      self.map.addControl(click);
      click.activate();

      return self;
    };
  }

  return {
    makeMap: function(element, options) { return new Map(element, options); }
  };

})();
