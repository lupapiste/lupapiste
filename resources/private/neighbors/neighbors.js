(function() {
  "use strict";

  var applicationDrawStyle = {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF", pointRadius: 6};
  var neighbourDrawStyle = {fillColor: "rgb(243,145,41)", // $lp-orange
                            fillOpacity: 0.50,
                            strokeColor: "#000",
                            pointRadius: 6,
                            strokeWidth: 3};
  var applicationId;

  var borderCache = {};

  function Model() {
    var self = this;

    self.getApplicationWKT = null;
    self.applicationAreaLoading = ko.observable(false);

    self.getNeighbourWKT = null;
    self.neighborAreasLoading = ko.observable(false);

    self.areasLoading = ko.pureComputed(function() {
      return self.applicationAreaLoading() || self.neighborAreasLoading();
    });

    self.applicationId = lupapisteApp.models.application.id;
    self.permitType = lupapisteApp.models.application.permitType;
    self.neighbors = ko.observableArray();
    self.neighborId = ko.observable();
    self.map = null;

    self.x = 0;
    self.y = 0;

    var neighborSkeleton = {propertyId: undefined,
                            owner: {
                                address: {
                                  city: undefined,
                                  street: undefined,
                                  zip: undefined
                                },
                                businessID: undefined,
                                email: undefined,
                                name: undefined,
                                nameOfDeceased: undefined,
                                type: undefined
                            }};

    function ensureNeighbors(neighbor) { // ensure neighbors have correct properties defined
      var n = _.defaults(neighbor, neighborSkeleton);
      n.owner = _.defaults(n.owner, neighborSkeleton.owner); // _.defaults is not deep
      return n;
    }

    self.draw = function(propertyIds, drawingStyle, processing) {
      var processedIds = [];
      var found = [];
      var missing = [];

      _.each(propertyIds, function(p) {
        if (!_.includes(processedIds, p)) {
          if (borderCache[p]) {
            _.each(borderCache[p], function(wkt) {found.push(wkt);});
          } else {
            missing.push(p);
          }
          processedIds.push(p);
        }
      });

      self.map.drawDrawings(found, {}, drawingStyle);

      if (!_.isEmpty(missing)) {
        ajax.datatables("property-borders", {propertyIds: missing})
          .success(function(resp) {
            _.each(_.groupBy(resp.wkts, "kiinttunnus"), function(m,k) {
              borderCache[k] = _.map(m, "wkt");
            });
            self.map.drawDrawings(_.map(resp.wkts, "wkt"), {}, drawingStyle);
          })
          .onError("error.integration.ktj-down", notify.ajaxError)
          .processing(processing)
          .call();
      }
    };

    self.init = function(application) {
      var location = application.location,
          x = location.x,
          y = location.y;
      var neighbors = _.map(application.neighbors, ensureNeighbors);

      if (!self.map) {
        self.map = gis.makeMap("neighbors-map", {zoomWheelEnabled: false, drawingControls: true});
        self.map.updateSize().center(x, y, 13).add({x: x, y:y});
      } else {
        self.map.updateSize().clear().add({x: x, y:y});
        if (self.x !== x || self.y !== y) {
          self.map.center(x, y);
        }
      }

      self.x = x;
      self.y = y;

      self.neighbors(neighbors).neighborId(null);

      self.getApplicationWKT = self.draw([application.propertyId], applicationDrawStyle, self.applicationAreaLoading);
      self.getNeighbourWKT = self.draw(_.map(neighbors, "propertyId"), neighbourDrawStyle, self.neighborAreasLoading);
    };

    function openEditDialog(params) {
      hub.send("show-dialog", {ltitle: "neighbors.edit.title",
                               component: "neighbors-edit-dialog",
                               componentParams: params,
                               size: "medium"});
    }

    self.edit = function(neighbor) {
      openEditDialog({neighbor: neighbor});
    };

    self.add = function() {
      openEditDialog();
    };

    self.done = function() {
      pageutil.openApplicationPage({id: applicationId}, "statement");
    };

    self.remove = function(neighbor) {
      self.neighborId(neighbor.id);
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("neighbors.remove-dialog.title"),
        loc("neighbors.remove-dialog.message"),
        {title: loc("yes"), fn: self.removeNeighbor},
        {title: loc("no")}
      );
      return self;
    };

    self.removeNeighbor = function() {
      ajax
        .command("neighbor-remove", {id: self.applicationId(), neighborId: self.neighborId()})
        .complete(_.partial(repository.load, self.applicationId(), _.noop))
        .call();
      return self;
    };
  }

  var model = new Model();

  function openOwnersDialog(params) {
    hub.send("show-dialog", { ltitle: "neighbor.owners.title",
                              size: "large",
                              component: "neighbors-owners-dialog",
                              componentParams: params });
  }

  hub.subscribe("LupapisteEditingToolbar::featureAdded", openOwnersDialog);
  hub.subscribe({eventType:"dialog-close", id:"neighbors-owners-dialog"}, function() {
    if (model.map) {
      model.map.clearManualDrawings();
    }
  });

  hub.onPageLoad("neighbors", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  hub.onPageUnload("neighbors", function() {
    if (model.getApplicationWKT) {
      model.getApplicationWKT.abort();
      model.getApplicationWKT = null;
    }
    if (model.getNeighbourWKT) {
      model.getNeighbourWKT.abort();
      model.getNeighbourWKT = null;
    }
    if (model.map) {
      model.map.clear();
    }

    // Could reset borderCache here to save memory?
  });

  repository.loaded(["neighbors"], function(application) {
    if (applicationId === application.id) {
      model.init(application);
    }
  });

  $(function() {
    $("#neighbors-content").applyBindings(model);
  });
})();
