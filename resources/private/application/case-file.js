(function() {
  "use strict";

  var model = function(params) {
    var self = this;
    self.application = params.application;
    self.caseFile = ko.observableArray();
    ajax.query("case-file-data", {id: params.application.id})
      .success(function(data) {
        self.caseFile(data.process);
      })
      .call();
        self.hasProcessMetadata = !_.isEmpty(ko.unwrap(self.application.processMetadata));
    self.showTosMetadata = ko.observable(false);
    self.toggleTosMetadata = function() {
      self.showTosMetadata(!self.showTosMetadata());
    }
  };

  ko.components.register("case-file", {
    viewModel: model,
    template: {element: "case-file-template"}
  });

})();
