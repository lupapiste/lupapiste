(function() {
  "use strict";

  var drawStyle = {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF", pointRadius: 6};
  var applicationId;

  function Model() {
    var self = this;

    self.applicationId = ko.observable();
    self.neighbors = ko.observableArray();
    self.neighborId = ko.observable();
    self.map = null;
    self.getWKT = null;

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

    self.init = function(application) {
      var location = application.location,
          x = location.x,
          y = location.y;
      var neighbors = _.map(application.neighbors, ensureNeighbors);

      if (!self.map) {
        self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click);
        self.map.updateSize().center(x, y, 12);
      } else {
        self.map.updateSize().center(x, y);
      }

      self
        .applicationId(application.id)
        .neighbors(neighbors)
        .neighborId(null)
        .map.clear().add({x: x, y: y});

      if (!_.isEmpty(neighbors)) {
        self.getWKT = ajax.datatables("property-borders", {propertyIds: _.pluck(neighbors, "propertyId")})
          .success(function(resp) {
            self.map.drawDrawings(resp.wkts, {}, drawStyle);
console.log(resp);
          })
          .call();
      }
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

    self.click = function(x, y) {
      hub.send("show-dialog", { ltitle: "neighbor.owners.title",
                                size: "large",
                                component: "neighbors-owners-dialog",
                                componentParams: {x: x,
                                                  y: y} });
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

  hub.onPageLoad("neighbors", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  hub.onPageUnload("neighbors", function() {
    if (model.getWKT) {
      model.getWKT.abort();
      model.getWKT = null;
    }
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
