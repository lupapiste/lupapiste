LUPAPISTE.LeafletMapModel = function(params) {
  "use strict";

  var self = this;

  self.id = params.id || util.randomElementId("map-component");

  var data = params.geoJsonFeatures;

  var map;

  var geoJsonLayer;

  function updateLayer(data) {
    console.log("data", data);
    if (map && geoJsonLayer) {
      geoJsonLayer.clearLayers();
      if (data.length > 0) {
        console.log("addData");
        geoJsonLayer.addData(data);
        map.fitBounds(geoJsonLayer.getBounds());
      }
    }
  }

  ko.computed(function() {
    updateLayer(data().features);
  });
  // data.subscribe(function(val) {
  //   updateLayer(val);
  // });

  function onEachFeature(feature, layer) {
    var name = util.getIn(feature, ["properties", "nimi"]) || "";
    layer.bindPopup(name);
  }

  // Defer until ko bindings are done
  _.defer(function() {
    map = L.map(self.id).setView([51.505, -0.09], 13);
    L.tileLayer("http://{s}.tile.osm.org/{z}/{x}/{y}.png", {
      attribution: "&copy; <a href='http://osm.org/copyright'>OpenStreetMap</a> contributors"
    }).addTo(map);
    geoJsonLayer = L.geoJson(null, {
      onEachFeature: onEachFeature
    }).addTo(map);
    updateLayer(data().features);
  });
};
