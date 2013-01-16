;(function() {

  function Model() {
    var self = this;
    
    self.application = null;

    self.title = ko.observable();
    self.url = ko.observable();
    
    self.init = function(application) {
      self.application = application;
      self.title(application.title);
      self.url("#!/application/" + application.id);
      console.log("INIT:", application);
      return self;
    };
    
    self.addOperation = function() {
      ajax.command("add-operation", {id: self.application.id, operation: "Fancy operation"})
        .success(function(d) {
          window.location.hash = self.url();
        })
        .call();
      return false;
    };
    
  }
    
  var model = new Model();
  var currentId;

  hub.onPageChange("add-operation", function(e) {
    currentId = e.pagePath[0];
    hub.send("load-application", {id: currentId});
    console.log("LOADING:");
  });

  hub.subscribe("application-loaded", function(e) {
    if (currentId === e.applicationDetails.application.id) {
      console.log("LOADED:");
      model.init(e.applicationDetails.application);
    }
  });

  $(function() {
    ko.applyBindings(model, $("#add-operation")[0]);
  });
  
})();
