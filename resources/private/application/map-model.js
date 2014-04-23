LUPAPISTE.MapModel = function() {
  "use strict";
  var self = this;

  var currentAppId = null;
  var applicationMap = null;
  var inforequestMap = null;
  var inforequestMarkerMap = null;
  var location = null;
  var drawings = null;
  var drawStyle = {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF", pointRadius: 6};

  var createMap = function(divName) {
    return gis.makeMap(divName, false).center(404168, 6693765, features.enabled("use-wmts-map") ? 14 : 12);
  };

  var getOrCreateMap = function(kind) {
    if (kind === "application") {
      if (!applicationMap) applicationMap = createMap("application-map");
      return applicationMap;
    } else if (kind === "inforequest") {
      if (!inforequestMap) inforequestMap = createMap("inforequest-map");
      return inforequestMap;
    } else if (kind === "inforequest-markers") {
      if (!inforequestMarkerMap) inforequestMarkerMap = createMap("inforequest-marker-map");
      return inforequestMarkerMap;
    } else {
      throw "Unknown kind: " + kind;
    }
  };

  self.refresh = function(application) {
    currentAppId = application.id;

    location = application.location;
    var x = location.x;
    var y = location.y;

    if (x === 0 && y === 0) {
      $('#application-map').css("display", "none");
    } else {
      $('#application-map').css("display", "inline-block");
    }

    drawings = application.drawings;

    var map = getOrCreateMap(application.infoRequest ? "inforequest" : "application");
    map.clear().center(x, y, features.enabled("use-wmts-map") ? 14 : 10).add(x, y);
    if (drawings) {
      map.drawDrawings(drawings, {}, drawStyle);
    }
    if (application.infoRequest) {
      map = getOrCreateMap("inforequest-markers");
      map.clear().center(x, y, features.enabled("use-wmts-map") ? 14 : 10); //.add(x, y);

      // get relevant markers
      ajax
        .query("inforequest-markers", {id: currentAppId, x: x, y: y})
        .success(function(data) {

          console.log("inforequest-markers success, data: ", data);
//          console.log("same location: ", data["sameLocation"]);
//          console.log("same operations: ", data["sameOperation"]);
//          console.log("others: ", data["others"]);

          // TODO: Set the markers onto the map

//          console.log("TEST, adding a sameOperation marker to ", x+10, ", ", y+10)
//          map.add(x+10, y+10, "sameOperation"); // TEST

          _.forEach(data["sameLocation"], function(ir) {
            console.log("same location: ", ir.location);
            map.add(ir.location.x, ir.location.y, "sameLocation");
          });

          _.forEach(data["sameOperation"], function(ir) {
            console.log("same operations: ", ir.location);
            map.add(ir.location.x, ir.location.y, "sameOperation");
          });

          _.forEach(data["others"], function(ir) {
            console.log("others: ", ir.location);
            map.add(ir.location.x, ir.location.y, "others");
          });

          //repository.load(currentAppId);
        })
        .call();

    }
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
          "name": d.name ||"",
          "desc": d.desc || "",
          "category": d.category || "",
          "geometry": d.geometry || "",
          "area": d.area || "",
          "height": d.height || "",
          "length": d.length || ""
        };});

      hub.send("oskari-show-shapes", {
        drawings: oskariDrawings,
        style: drawStyle,
        clear: true
      });
    }

    var x = (location && location.x) ? location.x : 0;
    var y = (location && location.y) ? location.y : 0;
    hub.send("oskari-center-map", {
      data:  [{location: {x: x, y: y}, iconUrl: "/img/map-marker.png"}],
      clear: true
    });
  });

  // When a shape is drawn in Oskari map, save it to application
  hub.subscribe("oskari-save-drawings", function(e) {
    if (_.isArray(e.data.drawings)) {
      ajax.command("save-application-drawings", {id: currentAppId, drawings: e.data.drawings})
      .success(function() {
        repository.load(currentAppId);
      })
      .call();
    }
  });

};
