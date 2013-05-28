(function() {
  "use strict";

  var neighbors = "neighbors";

  function Model() {
    var self = this;
    
    self.application = ko.observable();
    
    self.init = function(application) {
      console.log("init:", application);
      self.application(application);
    }
  }
  
  var applicationId;
  var model = new Model();
  
  hub.onPageChange(neighbors, function(e) {
    console.log("Here we are!", e);
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
