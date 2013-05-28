(function() {
  "use strict";

  var neighbors = "neighbors";
  
  function Model() {
    var self = this;
    
    self.application = ko.observable();
    self.neighbors = ko.observableArray();
    self.map = null;
    self.propertyId = function(v) { console.log("propertyId:", v); };
    self.requestContext = new RequestContext();
    self.beginUpdateRequest = function() { self.requestContext.begin(); return self; };
    self.searchPropertyId = function(x, y) { locationSearch.propertyIdByPoint(self.requestContext, x, y, self.propertyId); return self;  };
  
    self.click = function(x, y) { self.beginUpdateRequest().searchPropertyId(x, y); return false; };
    
    self.init = function(application) {
      if (!self.map) self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click);
      var location = application.location,
          x = location.x,
          y = location.y;
      self
        .application(application)
        .neighbors(_.map(application.neighbors, function(data, propertyId) { data.propertyId = propertyId; return data; }))
        .map.updateSize().clear().center(x, y, 11).add(x, y);
      console.log("N", self.application(), self.neighbors());
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
