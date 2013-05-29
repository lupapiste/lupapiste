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
  
  function toObservables(o) {
    return _.reduce(o, function(r, v, k) { r[k] = _.isString(v) ? ko.observable(v) : toObservables(v); return r; }, {});
  }
  
  function toNeighbors(neighbors, propertyId) {
    return _.map(neighbors, function(neighbor, id) { neighbor.neighborId = id; return neighbor; });
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
      if (!self.map) self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click);
      var location = application.location,
          x = location.x,
          y = location.y;
      self
        .applicationId(application.id)
        .neighbors(toNeighbors(application.neighbors))
        .map.updateSize().clear().center(x, y, 11).add(x, y);
    };
    
    self.edit   = function(neighbor) { editModel.init(neighbor).edit().open(); };
    self.add    = function()         { editModel.init().edit().open(); };
    self.click  = function(x, y)     { editModel.init().search(x, y).open(); };
    self.remove = function(neighbor) { /* TODO */ console.log("remove:", neighbor); };
  }
  
  function EditModel() {
    var self = this;

    self.status = ko.observable();
    self.statusInit         = 0;
    self.statusSearch       = 1;
    self.statusEdit         = 2;
    self.statusSearchFailed = 3;

    self.init = function(n) {
      var neighbor = n || {},
          owner = neighbor.owner || {},
          address = owner.address || {};
      return self
        .status(self.statusInit)
        .neighborId(neighbor.neighborId)
        .propertyId(neighbor.propertyId)
        .name(owner.name)
        .street(address.street)
        .city(address.city)
        .zip(address.zip)
        .email(owner.email);
    };

    self.edit = function() { return self.status(self.statusEdit); }
    self.search = function(x, y) { return self.status(self.statusSearch).beginUpdateRequest().searchPropertyId(x, y); };
    self.beginUpdateRequest = function() { self.requestContext.begin(); return self; };
    self.searchPropertyId = function(x, y) { locationSearch.propertyIdByPoint(self.requestContext, x, y, self.propertyIdFound, self.propertyIfNotFound); return self; };
    self.propertyIdFound = function(propertyId) { return self.propertyId(propertyId).status(self.statusEdit).focusName(); };
    self.propertyIfNotFound = function() { return self.status(self.statusSearchFailed); };
    self.cancelSearch = function() { self.status(self.statusEdit).requestContext.begin(); return self; };
    self.focusName = function() { $("#neighbors-edit-name").focus(); return self; };

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
    
    self.open = function() { LUPAPISTE.ModalDialog.open("#dialog-edit-neighbor"); return self; };
    self.save = function() { console.log("SAVE!"); LUPAPISTE.ModalDialog.close(); return self; };
    // self.neighbors.push(makeNew(propertyId));
    
    self.requestContext = new RequestContext();
  }
  
  hub.onPageChange("neighbors", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  repository.loaded(["neighbors"], function(application) {
    if (applicationId === application.id) model.init(application);
  });

  $(function() {
    $("#neighbors-content").applyBindings(model);
    $("#dialog-edit-neighbor").applyBindings(editModel).find("form").placeholderize();
  });

})();
