LUPAPISTE.MapModel = function(authorizationModel) {
  "use strict";
  var self = this;

  var currentAppId = null;
  var inforequestMarkerMap = null; // TODO will be replaced by new next gen React map
  var location = null;


  var createMap = function(divName) {
    return gis.makeMap(divName, {zoomWheelEnabled: false});
  };

  var getOrCreateMap = function(kind) {
    if (kind === "inforequest-markers") {
      if (!inforequestMarkerMap) {
        inforequestMarkerMap = createMap("inforequest-marker-map");

        inforequestMarkerMap.setMarkerClickCallback(
          function(matchingMarkerContents) {
            if (matchingMarkerContents) {
              $("#inforequest-marker-map-contents")
                .html(_.join(matchingMarkerContents, ""))
                .show();
            }
          }
        );

        inforequestMarkerMap.setMarkerMapCloseCallback(
          function() { $("#inforequest-marker-map-contents").html("").hide(); }
        );
      }
      return inforequestMarkerMap;
    } else {
      throw "Unknown kind: " + kind; // some kinds were removed  as of MAP-37
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
    currentAppId = application.id;

    var x = application.location.x;
    var y = application.location.y;

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

};
