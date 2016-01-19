(function() {
  "use strict";

  var model = function(params) {
    var self = this;
    self.application = params.application;
    self.caseFile = ko.observableArray();

    var fetchCaseFile = function() {
      ajax.query("case-file-data", {id: params.application.id})
        .success(function (data) {
          self.caseFile(data.process);
        })
        .call();
    };
    fetchCaseFile();

    self.hasProcessMetadata = !_.isEmpty(ko.unwrap(self.application.processMetadata));
    self.showTosMetadata = ko.observable(false);

    self.toggleTosMetadata = function() {
      self.showTosMetadata(!self.showTosMetadata());
    };

    hub.subscribe("application-loaded", function(e) {
      fetchCaseFile();
    });

  };

  ko.components.register("case-file", {
    viewModel: model,
    template: {element: "case-file-template"}
  });

})();
