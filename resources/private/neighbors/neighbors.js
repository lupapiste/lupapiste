(function() {
  "use strict";

  var neighbors = "neighbors";
  
  function Model() {
    var self = this;
    
    self.application = ko.observable();
    self.map = null;

    self.click = function() { console.log("click:", arguments); };
    
    self.init = function(application) {
      if (!self.map) self.map = gis.makeMap("neighbors-map", false).addClickHandler(self.click);
      var location = application.location,
          x = location.x,
          y = location.y;
      self.application(application).map.updateSize().clear().center(x, y, 11).add(x, y);
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
