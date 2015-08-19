LUPAPISTE.OpenlayersMapModel = function(params) {
  "use strict";

  var self = this;

  self.id = params.id || util.randomElementId("map-component");

  var data = params.geoJsonFeatures;

  var map;

  function updateLayer() {
    if (map && !ol.extent.isEmpty(vectorLayer.getSource().getExtent())) {
      view.fit(vectorLayer.getSource().getExtent(), map.getSize());
    }
  }

  proj4.defs("EPSG:3067","+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs");

  // Shape styles
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

  data.subscribe(function(val) {
    vectorSource.clear();
    var features = (new ol.format.GeoJSON()).readFeatures(val);
    vectorSource.addFeatures(features);
    updateLayer();
  });

  // function onEachFeature(feature, layer) {
  //   var name = util.getIn(feature, ["properties", "nimi"]) || "";
  //   layer.bindPopup(name);
  // }

  var xhr = new XMLHttpRequest();
  var mapServer = LUPAPISTE.config.maps["proxyserver-wmts"];

  if (mapServer.indexOf(",") > -1) {
    mapServer = mapServer.split(",");
  }

  var urls = util.withSuffix(mapServer, "/maasto?");

  // get WMTS server capabilities
  xhr.open("GET", _.first(urls) + "service=wmts&request=getcapabilities&version=1.0.0&layer=taustakartta", true);

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
    updateLayer();
  }
};
