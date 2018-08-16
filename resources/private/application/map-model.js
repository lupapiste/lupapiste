LUPAPISTE.MapModel = function(authorizationModel) {
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
    return gis.makeMap(divName, {zoomWheelEnabled: false});
  };

  var getOrCreateMap = function(kind) {
    if (kind === "application") {
      if (!applicationMap) {
        applicationMap = createMap("application-map");
      }
      return applicationMap;
    } else if (kind === "inforequest") {
      if (!inforequestMap) {
        inforequestMap = createMap("inforequest-map");
      }
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
    var html = $("<div>");

    _.each(irs, function(ir) {
      var card          = $("<div>").attr("class", "inforequest-card").attr("data-test-id", "inforequest-card-" + ir.id);
      var partTitle     = $("<h2>").attr("class", "operation-title").attr("data-test-id", "inforequest-title").text(ir.title + " - " + ir.authName);
      var partOperation = $("<h3>").attr("class", "operation-type").attr("data-test-id", "inforequest-operation").text(ir.operation);
      card.append(partTitle).append(partOperation);

      _.each(ir.comments, function(com) {
        var partComment = $("<div>").attr("class", "inforequest-comment");

        var commentTitle         = com.type === "authority" ? loc("inforequest.answer.title") : loc("inforequest.question.title");
        var commentTimestamp     = " (" +
                                   (com.type === "authority" ?
                                     com.name + " " + moment(com.time).format("D.M.YYYY HH:mm") :
                                     moment(com.time).format("D.M.YYYY HH:mm")) +
                                   ")";
        var partCommentTitle     = $("<span>").attr("class", "comment-type").text(commentTitle);
        var partCommentTimestamp = $("<span>").attr("class", "timestamp").text(commentTimestamp);

        var partCommentText = $("<blockquote>").attr("class", "inforequest-comment-text").text(com.text);

        partComment = partComment.append(partCommentTitle).append(partCommentTimestamp).append(partCommentText);
        card.append(partComment);
      });

      // no link is attached to currently opened inforequest
      if (ir.link) {
        var partLink      = $("<a>").attr("data-test-id", "inforequest-link").attr("href", ir.link).text(loc("inforequest.openlink"));
        card.append(partLink);
      }

      html.append(card);
    });

    return html.html();
  };

  var setRelevantMarkersOntoMarkerMap = function(map, appId, x, y) {
    if (authorizationModel.ok("inforequest-markers")) {
      ajax
      .query("inforequest-markers", {id: currentAppId, lang: loc.getCurrentLanguage(), x: x, y: y})
      .success(function(data) {

        var markerInfos = [];

        // same location markers
        markerInfos.push({
          x: _.get( data, "sameLocation[0].location.x"),
          y: _.get( data, "sameLocation[0].location.y"),
          iconName: "sameLocation",
          contents: formMarkerHtmlContents( data.sameLocation ),
          isCluster: _.size( data.sameLocation ) > 1 ? true : false
        });

        // same operation markers
        _.each(data.sameOperation, function(ir) {
          markerInfos.push({
            x: ir.location.x,
            y: ir.location.y,
            iconName: "sameOperation",
            contents: formMarkerHtmlContents(ir),
            isCluster: false
          });
        });

        // other markers
        _.each(data.others, function(ir) {
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
      .call();
    }
  };

  self.refresh = function(application) {
    var map;
    var mapKind = application.infoRequest ? "inforequest" : "application";
    currentAppId = application.id;

    var x = application.location.x;
    var y = application.location.y;

    if (!x && !y) {
      $("#application-map").css("display", "none");
      $("#inforequest-map").css("display", "none");
      return;
    } else {
      $("#application-map").css("display", "inline-block");
      $("#inforequest-map").css("display", "inline-block");
    }

    drawings = application.drawings;

    try {
      map = getOrCreateMap(mapKind);
    } catch (e) {
      // Trying to figure out when LPK-816 occurs
      error("Unable to get or create map for " + mapKind, e);
      return;
    }

    map.clear().updateSize().center(x, y, 14).add({x: x, y: y});

    // In some cases, e.g. in location {x: 461586.443, y: 7472906.0969994}
    // map is initialized to wrong size in IE 11.
    // Workaround: initialize different zoom level and zoom into correct level.
    if (location === null || (x !== location.x && y !== location.y)) {
      map.center(x, y, 3).zoomTo(14);
    }

    if (drawings) {
      map.drawDrawings(drawings, {}, drawStyle);
    }
    if (application.infoRequest && pageutil.getPage() === "inforequest") {

      // Marker map visibility handling [https://issues.solita.fi/browse/LPK-79].
      // In html, could not hide "#inforequest-marker-map" with knockout's "visible" binding, because Openlayers would then try to incorrectly initialize the map.
      // Then, when accessing inforequest with applicant user, loading of the map fails with "Cannot read property 'w' of null".
      // ->  Resolution: hiding the map with jQuery
      //
      // Other option that could be used here, instead of jQuery show/hide:
      // - re-constructing the map every time  -> remember to call destroy() for the previous map before contructing the new one,
      //   to prevent duplicate maps from generating into html.
      //   I.e. call "if (inforequestMarkerMap) inforequestMarkerMap.clear().destroy();" (create a forwarding destroy() method to gis.js)
      //   But this is even uglier than the jQuery option.
      //
      try {
        if (authorizationModel.ok("inforequest-markers") && !_.isUndefined($("#inforequest-marker-map"))) {
          var irMarkersMap = getOrCreateMap("inforequest-markers");
          irMarkersMap.clear().updateSize().center(x, y, 14);
          setRelevantMarkersOntoMarkerMap(irMarkersMap, currentAppId, x, y);
          $("#inforequest-marker-map").show();
        } else {
          $("#inforequest-marker-map").hide();
        }
      } catch (e) {
        error("Unable to get or create map for inforequest-markers", e);
        return;
      }

    }

    location = application.location;
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
      data:  [{location: {x: x, y: y}, iconUrl: "/lp-static/img/map-marker-orange.png"}],
      clear: true
    });
  });

  // When a shape is drawn in Oskari map, save it to application
  hub.subscribe("oskari-save-drawings", function(e) {
    var data = e.data;
    var drawings = data.drawings ? JSON.parse(data.drawings) : undefined;
    if (_.isArray(drawings)) {
      ajax.command("save-application-drawings", {id: currentAppId, drawings: drawings})
      .success(function() {
        repository.load(currentAppId);
      })
      .call();
    }
  });

};
