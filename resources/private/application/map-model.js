LUPAPISTE.MapModel = function() {
  "use strict";
  var self = this;

  var currentAppId = null;
  var applicationMap = null;
  var inforequestMap = null;
  var location = null;
  var drawings = null;


  var createMap = function(divName) { return gis.makeMap(divName, false).center([{x: 404168, y: 6693765}], 12); };

  var getOrCreateMap = function(kind) {
    if (kind === "application") {
      if (!applicationMap) applicationMap = createMap("application-map");
      return applicationMap;
    } else if (kind === "inforequest") {
      if (!inforequestMap) inforequestMap = createMap("inforequest-map");
      return inforequestMap;
    } else {
      throw "Unknown kind: " + kind;
    }
  };


  self.refresh = function(application) {
    currentAppId = application.id;

    location = application.location;
    var x = location.x;
    var y = location.y;

    if(x === 0 && y === 0) {
      $('#application-map').css("display", "none");
    } else {
      $('#application-map').css("display", "inline-block");
    }

    drawings = application.drawings;

    var map = getOrCreateMap(application.infoRequest ? "inforequest" : "application");
    map.clear().center(x, y, 10).add(x, y).drawDrawings(drawings);
  };

  self.updateMapSize = function(kind) {
    getOrCreateMap(kind).updateSize();
  };


  // Oskari events

  // When Oskari map has initialized itself, draw shapes and the marker
  hub.subscribe("oskari-map-initialized", function() {
    if (drawings && drawings.length > 0) {
      var oskariDrawings = _.map(drawings, function(d) {
        return {
          "id": d.id,
          "name": d.name? d.name :"",
          "desc": d.desc ? d.desc : "",
          "category": d.category ? d.category : "",
          "geometry": d.geometry ? d.geometry : "",
          "area": d.area? d.area : "",
          "height": d.height? d.height : ""
        }});

      hub.send("oskari-show-shapes", {
        drawings: oskariDrawings,
        style: {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF"},
        clear: true
      });
    }

    var x = (location && location().x) ? location().x() : 0;
    var y = (location && location().y) ? location().y() : 0;
    hub.send("oskari-center-map", {
      data:  [{location: {x: x, y: y}, iconUrl: "/img/map-marker.png"}],
      clear: true
    });
  });

  // When a shape is drawn in Oskari map, save it to application
  hub.subscribe("oskari-save-drawings", function(e) {
    console.log("oskari-save-drawings, e: ", e);
    ajax.command("save-application-drawings", {id: currentAppId, drawings: e.data.drawings})
    .success(function() {
      repository.load(currentAppId);
    })
    .call();
  });

};