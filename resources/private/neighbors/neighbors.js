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
  
  function Model() {
    var self = this;
    
    self.applicationId = ko.observable();
    self.neighbors = ko.observableArray();
    self.map = null;
    self.requestContext = new RequestContext({begin: ajaxOn, done: ajaxOff});
    self.beginUpdateRequest = function() { self.requestContext.begin(); return self; };
  
    self.click = function(x, y) { self.beginUpdateRequest().searchPropertyId(x, y); return false; };
    self.searchPropertyId = function(x, y) { locationSearch.propertyIdByPoint(self.requestContext, x, y, self.add); return self;  };
    
    self.init = function(application) {
      console.log("N:", application.neighbors, toNeighbors(application.neighbors));
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
    self.add = function(propertyId) { self.neighbors.push(makeNew(propertyId)); };
  }
  
  var applicationId;
  var model = new Model();
  
  hub.onPageChange(neighbors, function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  repository.loaded([neighbors], function(application) {
    if (applicationId === application.id) model.init(application);
  });

  $(function() {
    $("#neighbors").applyBindings(model);
  });

})();
