LUPAPISTE.OpenlayersMapModel = function(params) {
  "use strict";

  var self = this;

  self.id = params.id || util.randomElementId("map-component");

  var features = params.geoJsonFeatures;

  var map;

  proj4.defs("EPSG:3067","+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs");

  // style for multipolygon
  var styles = {
    "MultiPolygon": [new ol.style.Style({
      stroke: new ol.style.Stroke({
        color: "rgb(92, 138, 230)",
        width: 2
      }),
      fill: new ol.style.Fill({
        color: "rgba(102, 153, 255, 0.4)"
      })
    })]
  };

  var view = new ol.View({
    projection: "EPSG:3067"
  });

  var vectorSource = new ol.source.Vector();

  var vectorLayer = new ol.layer.Vector({
    source: vectorSource,
    style: function(feature) {
      return styles[feature.getGeometry().getType()];
    }
  });

  var popup = new ol.Overlay.Popup();

  // fit viewport to feature layer extent
  function updateView() {
    if (map) {
      map.updateSize();
      if (map.getSize() && !ol.extent.isEmpty(vectorLayer.getSource().getExtent())) {
        view.fit(vectorLayer.getSource().getExtent(), map.getSize());
      }
    }
  }

  function updateMap(data) {
    vectorSource.clear();
    if (data) {
      vectorSource.addFeatures((new ol.format.GeoJSON()).readFeatures(data));
    }
    updateView();
  }

  // update map when features change
  ko.computed(function() {
    updateMap(features());
  }).extend({throttle: 250});

  var xhr = new XMLHttpRequest();
  var mapServer = LUPAPISTE.config.maps["proxyserver-wmts"];

  if (mapServer.indexOf(",") > -1) {
    mapServer = mapServer.split(",");
  }

  var urls = util.withSuffix(mapServer, "/maasto?");

  // get WMTS server capabilities
  xhr.open("GET", "/proxy/wmts/maasto?service=wmts&request=getcapabilities&version=1.0.0&layer=taustakartta", true);

  xhr.onload = function() {
    if (xhr.status === 200) {
      initMap();
    }
  };

  xhr.send();

  function initMap() {

    // config from http://epsg.io/3067
    proj4.defs("EPSG:3067","+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs");

    var extent = [50199.4814, 6582464.0358, 761274.6247, 7799839.8902];
    ol.proj.get("EPSG:3067").setExtent(extent);

    var parser = new ol.format.WMTSCapabilities();
    var capabilities = parser.read(xhr.responseXML);
    var options = ol.source.WMTS.optionsFromCapabilities(capabilities, {
                    layer: "taustakartta"
                  });
    // use proxy urls
    options.urls = urls;

    var source = new ol.source.WMTS(options);

    map = new ol.Map({
      layers: [
        new ol.layer.Tile({
          title: "Taustakartta",
          type: "base",
          visible: true,
          source: source
        }),
        vectorLayer
      ],
      target: self.id,
      view: view
    });

    updateView();

    map.addOverlay(popup);

    map.on("singleclick", function(evt) {
      popup.hide();

      // try to find feature from all visible layers under the cursor
      var feature = map.forEachFeatureAtPixel(evt.pixel, function(feature) {
        return feature;
      });

      if (feature) {
        // convert properties to lowercase i.e. NIMI -> nimi
        // mapKeys passes key at index 1, pass only that to util.lowerCase
        var props = _.mapKeys(feature.getProperties(), _.rearg(util.lowerCase, 1));
        popup.show(evt.coordinate, props.nimi);
      }
    });
  }

  var hubsub = hub.subscribe("page-load", updateView);

  self.dispose = function() {
    hub.unsubscribe(hubsub);
    if (map) {
      map.destroy();
    }
  };
};
