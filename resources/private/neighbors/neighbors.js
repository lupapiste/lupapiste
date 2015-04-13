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
      editModel.init(neighbor).edit().openEdit();
    };

    self.add    = function() {
      editModel.init().edit().openEdit();
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

  function EditModel() {
    var self = this;

    self.status = ko.observable();
    self.statusInit         = 0;
    self.statusEdit         = 2;

    self.init = function(n) {
      var neighbor = n || {},
          owner = neighbor.owner || {},
          address = owner.address || {};

      return self
        .status(self.statusInit)
        .id(applicationId)
        .neighborId(neighbor.id)
        .propertyId(neighbor.propertyId)
        .name(owner.name)
        .street(address.street)
        .city(address.city)
        .zip(address.zip)
        .email(owner.email)
        .type(owner.type)
        .nameOfDeceased(owner.nameOfDeceased)
        .businessID(owner.businessID)
        .pending(false);
    };

    self.edit = function() { return self.status(self.statusEdit); };
    self.focusName = function() { $("#neighbors-edit-name").focus(); return self; };

    self.id = ko.observable();
    self.neighborId = ko.observable();
    self.propertyId = ko.observable();
    self.name = ko.observable();
    self.street = ko.observable();
    self.city = ko.observable();
    self.zip = ko.observable();
    self.email = ko.observable();
    self.type = ko.observable();
    self.nameOfDeceased = ko.observable();
    self.businessID = ko.observable();
    self.pending = ko.observable(false);

    self.typeLabel = ko.computed(function() {
      var t = self.type();
      if (t) {
        return loc(["neighbors.owner.type", t]);
      } else {
        return null;
      }
    }, self);

    self.editablePropertyId = ko.computed({
      read: function() {
        return util.prop.toHumanFormat(self.propertyId());
      },
      write: function(newValue) {
        if (util.prop.isPropertyId(newValue)) {
          self.propertyId(util.prop.toDbFormat(newValue));
        }
      },
      owner: self
    });

    self.propertyIdOk = ko.computed(function() { return util.prop.isPropertyId(self.propertyId()); });
    self.emailOk = ko.computed(function() { return _.isBlank(self.email()) || util.isValidEmailAddress(self.email()); });
    self.disabled = ko.computed(function() { return self.pending() || !self.propertyIdOk() || !self.emailOk(); });

    var paramNames = ["id", "neighborId", "propertyId", "name", "street", "city", "zip", "email", "type", "businessID", "nameOfDeceased"];
    function paramValue(paramName) { return self[paramName](); }

    self.openEdit = function() { LUPAPISTE.ModalDialog.open("#dialog-edit-neighbor"); return self; };
    self.save = function() {
      ajax
        .command(self.neighborId() ? "neighbor-update" : "neighbor-add", _.zipObject(paramNames, _.map(paramNames, paramValue)))
        .pending(self.pending)
        .complete(_.partial(repository.load, self.id(), function(v) { if (!v) { LUPAPISTE.ModalDialog.close(); }}))
        .call();
      return self;
    };
  }

  var model = new Model();
  var editModel = new EditModel();
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
    $("#dialog-edit-neighbor").applyBindings(editModel);
    // $("#dialog-select-owners").applyBindings(ownersModel);
  });

})();
