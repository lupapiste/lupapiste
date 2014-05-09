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
      if (!inforequestMarkerMap) {
        inforequestMarkerMap = createMap("inforequest-marker-map");

        inforequestMarkerMap.setMarkerClickCallback(
          function(matchingMarkerContents) {
            if (matchingMarkerContents) {
              $("#inforequest-marker-map-contents").html(matchingMarkerContents).show();
            }
          }
        );

        inforequestMarkerMap.setMarkerMapCloseCallback(
          function() { $("#inforequest-marker-map-contents").html("").hide(); }
        );
      }
      return inforequestMarkerMap;
    } else {
      throw "Unknown kind: " + kind;
    }
  };

  var formMarkerHtmlContents = function(irs) {
    irs = _.isArray(irs) ? irs : [irs];
    var html = "";

    _.each(irs, function(ir) {
      html +=
        '<div class="inforequest-card" data-test-id="inforequest-card-' + ir.id + '">' +
          '<h2 class="operation-title" data-test-id="inforequest-title">' + ir.title + ' - ' + ir.authName + '</h2>' +
          '<h3 class="operation-type" data-test-id="inforequest-operation">' + ir.operation + '</h3>';

      _.each(ir.comments, function(com) {
        if (com.type === "authority") {
          html += "<div class='inforequest-comment'><span class='comment-type'>" + loc('inforequest.answer.title') + "</span> <span class='timestamp'>(" + com.name + " " + moment(com.time).format("D.M.YYYY HH:mm") + ")</span>";
        } else {
          html += "<div class='inforequest-comment'><span class='comment-type'>" + loc('inforequest.question.title') + "</span> <span class='timestamp'>(" + moment(com.time).format("D.M.YYYY HH:mm") + ")</span>";
        }
        html += '<blockquote>' + com.text + '</blockquote></div>';
      });

      // no link is attached to currently opened inforequest
      if (ir.link) {
        html += "<a data-test-id='inforequest-link' href='" + ir.link + "'>Avaa neuvontapyyntö</a>";
      }

      html += '</div>';
    });

    return html;
  };

  var setRelevantMarkersOntoMarkerMap = function(map, appId, x, y) {
    ajax
    .query("inforequest-markers", {id: currentAppId, lang: loc.getCurrentLanguage(), x: x, y: y})
    .success(function(data) {

      var markerInfos = [];

      // same location markers
      markerInfos.push({
        x: data["sameLocation"][0].location.x,
        y: data["sameLocation"][0].location.y,
        iconName: "sameLocation",
        contents: formMarkerHtmlContents( data["sameLocation"] ),
        isCluster: data["sameLocation"].length > 1 ? true : false
      });

      // same operation markers
      _.each(data["sameOperation"], function(ir) {
        markerInfos.push({
          x: ir.location.x,
          y: ir.location.y,
          iconName: "sameOperation",
          contents: formMarkerHtmlContents(ir),
          isCluster: false
        });
      });

      // other markers
      _.each(data["others"], function(ir) {
        markerInfos.push({
          x: ir.location.x,
          y: ir.location.y,
          iconName: "others",
          contents: formMarkerHtmlContents(ir),
          isCluster: false
        });
      });

      map.add(markerInfos);
    })
    .error(function(data) {
      //
      // Needed to have this error branch to prevent applicant from getting error popups on the UI.
      //
      if (data.text !== "error.unauthorized") {
        debug("Could not fetch inforequest markers", data);
      }
    })
    .call();
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
    map.clear().center(x, y, features.enabled("use-wmts-map") ? 14 : 10).add({x: x, y: y});
    if (drawings) {
      map.drawDrawings(drawings, {}, drawStyle);
    }
    if (application.infoRequest) {
      map = getOrCreateMap("inforequest-markers");
      map.clear().center(x, y, features.enabled("use-wmts-map") ? 14 : 10);
      setRelevantMarkersOntoMarkerMap(map, currentAppId, x, y);
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
