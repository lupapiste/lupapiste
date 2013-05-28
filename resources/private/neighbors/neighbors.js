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
  
  function Model() {
    var self = this;
    
    self.application = ko.observable();
    self.neighbors = ko.observableArray();
    self.map = null;
    self.addNeighbor = function(propertyId) { self.neighbors.push(makeNew(propertyId));};
    self.requestContext = new RequestContext();
    self.beginUpdateRequest = function() { self.requestContext.begin(); return self; };
    self.searchPropertyId = function(x, y) { locationSearch.propertyIdByPoint(self.requestContext, x, y, self.addNeighbor); return self;  };
  
    self.click = function(x, y) { self.beginUpdateRequest().searchPropertyId(x, y); return false; };
    
    self.init = function(application) {
      if (!self.map) self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click);
      var location = application.location,
          x = location.x,
          y = location.y;
      self
        .application(application)
        .neighbors(_.map(application.neighbors, function(data, propertyId) { data.propertyId = propertyId; data.state = "ready"; return data; }))
        .map.updateSize().clear().center(x, y, 11).add(x, y);
    }
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
