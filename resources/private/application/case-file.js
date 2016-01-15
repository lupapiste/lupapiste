(function() {
  "use strict";

  var model = function(params) {
    var self = this;
    self.caseFile = ko.observableArray();
    ajax.query("case-file-data", {id: params.application.id})
      .success(function(data) {
        self.caseFile(data.process);
      })
      .call();
  };

  ko.components.register("case-file", {
    viewModel: model,
    template: {element: "case-file-template"}
  });

})();
