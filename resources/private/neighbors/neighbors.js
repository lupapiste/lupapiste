(function() {
  "use strict";

  var applicationId;

  function Model() {
    var self = this;

    self.applicationId = ko.observable();
    self.neighbors = ko.observableArray();
    self.neighborId = ko.observable();
    self.map = null;

    self.init = function(application) {
      if (!self.map) {
        self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click);
      }
      var location = application.location,
          x = location.x,
          y = location.y;
      self
        .applicationId(application.id)
        .neighbors(application.neighbors)
        .neighborId(null)
        .map.updateSize().clear().center(x, y, 13).add({x: x, y: y});
    };

    function openEditDialog(params) {
      var loc = { title: "neighbors.edit.title",
                  submitButton: "save" };
      hub.send("show-dialog", {loc: loc,
                               component: "neighbors-edit",
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
      var loc = { title: "neighbor.owners.title",
                  submitButton: "save" };
      hub.send("show-dialog", { loc: loc,
                                component: "neighbors-owners",
                                componentParams: {x: x,
                                                  y: y} });
    };

    self.done = function() {
      window.location.hash = "!/application/" + applicationId + "/statement";
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
        .complete(_.partial(repository.load, self.applicationId(), util.nop))
        .call();
      return self;
    };
  }

  var model = new Model();

  hub.onPageLoad("neighbors", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
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
