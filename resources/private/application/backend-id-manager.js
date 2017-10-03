(function() {
  "use strict";

  var model = function(params) {
    var self = this;
    self.application = params.application;

    hub.subscribe("application-loaded", function() {

    });
  };

  ko.components.register("backend-id-manager", {
    viewModel: model,
    template: {element: "backend-id-manager-template"}
  });

})();
