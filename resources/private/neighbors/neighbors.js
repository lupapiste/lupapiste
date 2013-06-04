(function() {
  "use strict";

  function makeNew(propertyId) {
    return {
      propertyId: propertyId,
      owner: {
        name: ko.observable(""),
        address: {
          street: ko.observable(""),
          city: ko.observable(""),
          zip: ko.observable("")
        }
      },
      state: "new"
    };
  }

  var applicationId;
  var model = new Model();
  var editModel = new EditModel();

  function Model() {
    var self = this;

    self.applicationId = ko.observable();
    self.neighbors = ko.observableArray();
    self.map = null;

    self.init = function(application) {
      if (!self.map) { self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click); }
      var location = application.location,
          x = location.x,
          y = location.y;
      self
        .applicationId(application.id)
        .neighbors(application.neighbors)
        .map.updateSize().clear().center(x, y, 11).add(x, y);
    };

    self.edit   = function(neighbor) { editModel.init(neighbor).edit().open(); };
    self.add    = function()         { editModel.init().edit().open(); };
    self.click  = function(x, y)     { editModel.init().search(x, y).open(); };
    self.remove = function(neighbor) {
      // TODO: needs "Are you sure?" dialog.
      ajax
        .command("neighbor-remove", {id: applicationId, neighborId: neighbor.neighborId})
        .complete(_.partial(repository.load, applicationId, util.nop))
        .call();
      return self;
    };
    self.done = function() { window.location.hash = "!/application/" + applicationId + "/statement"; };
  }

  function EditModel() {
    var self = this;

    self.status = ko.observable();
    self.statusInit         = 0;
    self.statusSearch       = 1;
    self.statusEdit         = 2;
    self.statusSearchFailed = 3;

    self.init = function(data) {
      var data = data || {},
          neighbor = data.neighbor || {},
          owner = neighbor.owner || {},
          address = owner.address || {};
      return self
        .status(self.statusInit)
        .id(applicationId)
        .neighborId(data.neighborId)
        .propertyId(neighbor.propertyId)
        .name(owner.name)
        .street(address.street)
        .city(address.city)
        .zip(address.zip)
        .email(owner.email)
        .pending(false);
    };

    self.edit = function() { return self.status(self.statusEdit); };
    self.search = function(x, y) { return self.status(self.statusSearch).beginUpdateRequest().searchPropertyId(x, y); };
    self.beginUpdateRequest = function() { self.requestContext.begin(); return self; };
    self.searchPropertyId = function(x, y) { locationSearch.propertyIdByPoint(self.requestContext, x, y, self.propertyIdFound, self.propertyIfNotFound); return self; };
    self.propertyIdFound = function(propertyId) { return self.propertyId(propertyId).status(self.statusEdit).focusName(); };
    self.propertyIfNotFound = function() { return self.status(self.statusSearchFailed); };
    self.cancelSearch = function() { self.status(self.statusEdit).requestContext.begin(); return self; };
    self.focusName = function() { $("#neighbors-edit-name").focus(); return self; };

    self.id = ko.observable();
    self.neighborId = ko.observable();
    self.propertyId = ko.observable();
    self.name = ko.observable();
    self.street = ko.observable();
    self.city = ko.observable();
    self.zip = ko.observable();
    self.email = ko.observable();

    self.propertyIdOk = ko.computed(function() { return util.prop.isPropertyId(self.propertyId()); });
    self.emailOk = ko.computed(function() { return _.isBlank(self.email()) || util.isValidEmailAddress(self.email()); });
    self.ok = ko.computed(function() { return self.propertyIdOk() && self.emailOk(); });

    self.pending = function(pending) {
      // Should have ajax indicator too
      $("#dialog-edit-neighbor button").attr("disabled", pending ? "disabled" : null);
      return self;
    };

    var paramNames = ["id", "neighborId", "propertyId", "name", "street", "city", "zip", "email"];
    function paramValue(paramName) { return self[paramName](); }

    self.open = function() { LUPAPISTE.ModalDialog.open("#dialog-edit-neighbor"); return self; };
    self.save = function() {
      ajax
        .command(self.neighborId() ? "neighbor-update" : "neighbor-add", _.zipObject(paramNames, _.map(paramNames, paramValue)))
        .pending(self.pending)
        .complete(_.partial(repository.load, self.id(), function(v) { if (!v) { LUPAPISTE.ModalDialog.close(); }}))
        .call();
      return self;
    };

    // self.neighbors.push(makeNew(propertyId));

    self.requestContext = new RequestContext();
  }

  hub.onPageChange("neighbors", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  repository.loaded(["neighbors"], function(application) {
    if (applicationId === application.id) { model.init(application); }
  });

  $(function() {
    $("#neighbors-content").applyBindings(model);
    $("#dialog-edit-neighbor").applyBindings(editModel).find("form").placeholderize();
  });

})();
