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

    self.edit   = function(neighbor) {
      // editModel.init(neighbor).edit().openEdit();
      var loc = { title: "neighbors.edit.title",
                  submitButton: "save" };
      hub.send("show-dialog", {loc: loc,
                               contentName: "neighbors-edit",
                               contentParams: {neighbor: neighbor}});
    };

    self.add    = function() {
      // editModel.init().edit().openEdit();
      var loc = { title: "neighbors.edit.title",
                  submitButton: "save" };
      hub.send("show-dialog", {loc: loc,
                               contentName: "neighbors-edit",
                               contentParams: {}});
    };

    self.click  = function(x, y) {
      // ownersModel.init().search(x, y).openOwners();
      var loc = { title: "neighbor.owners.title",
                  submitButton: "save" };

      hub.send("show-dialog", { loc: loc,
                                contentName: "neighbors-owners",
                                contentParams: {x: x,
                                                y: y}
                              });
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
  // var editModel = new EditModel();
  // var ownersModel = new OwnersModel();

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
    // $("#dialog-edit-neighbor").applyBindings(editModel);
    // $("#dialog-select-owners").applyBindings(ownersModel);
  });

})();
