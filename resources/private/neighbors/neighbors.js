(function() {
  "use strict";

  var neighbors = "neighbors";
  
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
    return _.reduce(o, function(r, v, k) { console.log("O:", k, v); r[k] = _.isString(v) ? ko.observable(v) : toObservables(v); return r; }, {});
  }
  
  function toNeighbors(neighbors, propertyId) {
    return _.map(neighbors, function(neighbor, propertyId) { neighbor.propertyId = propertyId; return neighbor; });
  }
  
  function ajaxOn() { $("#neighbors .map-ajax").show(); }
  function ajaxOff() { $("#neighbors .map-ajax").hide(); }

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
    
    self.edit = function(neighbor) { console.log("edit:", neighbor); };
    self.remove = function(neighbor) { console.log("remove:", neighbor); };
    self.add = function() { editModel.init().edit().open(); };
    self.click = function(x, y) { editModel.init().search(x, y).open(); };
  }
  
  function EditModel() {
    var self = this;

    self.status = ko.observable();
    self.statusInit    = 0;
    self.statusSearch  = 1;
    self.statusEdit    = 2;

    self.init = function() {
      return self
        .status(self.statusInit)
        .propertyId("")
        .name("")
        .street("")
        .city("")
        .zip("")
        .email("");
    };

    self.edit = function() { return self.status(self.statusEdit); }
    self.search = function(x, y) { return self.status(self.statusSearch).beginUpdateRequest().searchPropertyId(x, y); };
    
    self.requestContext = new RequestContext();
    self.beginUpdateRequest = function() { self.requestContext.begin(); return self; };
    self.searchPropertyId = function(x, y) { locationSearch.propertyIdByPoint(self.requestContext, x, y, self.searchDone); return self;  };
    self.cancelSearch = function() { self.status(self.statusEdit).requestContext.begin(); return self; }
    
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
  }
  
  hub.onPageChange(neighbors, function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  repository.loaded([neighbors], function(application) {
    if (applicationId === application.id) model.init(application);
  });

  $(function() {
    $("#neighbors-content").applyBindings(model);
    $("#dialog-edit-neighbor").applyBindings(editModel);
  });

})();
