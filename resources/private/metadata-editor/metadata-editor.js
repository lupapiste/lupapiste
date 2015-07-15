(function() {
  "use strict";

  var model = function(params) {
    var self = this;
    self.attachmentId = params.attachmentId;
    self.metadata = params.metadata;
    self.editable = false;
    self.editedMetadata = ko.observable(self.metadata());
    self.schema = ko.observableArray();

    self.edit = function() {
      self.editable = true;
    };

    ajax.query('tos-metadata-schema')
      .success(function(data) {
        self.schema(data.schema);
      })
      .call();
  };

  ko.components.register("metadata-editor", {
    viewModel: model,
    template: {element: "metadata-editor-template"}
  });

})();