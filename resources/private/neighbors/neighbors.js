(function() {
  "use strict";

  var neighbors = "neighbors";
  var map = null;
  
  function initMap() {
    return gis.makeMap("neighbors-map", false).center([{x: 404168, y: 6693765}], 12);
  }
  
  function Model() {
    var self = this;
    
    self.application = ko.observable();
    
    self.init = function(application) {
      console.log("init:", application);
      if (!map) map = initMap();
      map.updateSize();
      self.application(application);
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
