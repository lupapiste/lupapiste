(function() {
  "use strict";

  var model = function(params) {
    var self = this;
    self.attachmentId = params.attachmentId;
    self.metadata = params.metadata();
    self.editable = ko.observable(false);
    self.editedMetadata = ko.observable(ko.mapping.fromJS(self.metadata));
    self.schema = ko.observableArray();
    // It does not seem to be possible to use visible: !editable, so we need another observable for that.
    self.displayOnly = ko.pureComputed(function() {
      return !self.editable();
    });

    ajax.query('tos-metadata-schema')
      .success(function(data) {
        self.schema(data.schema);
      })
      .call();

    self.edit = function() {
      self.editable(true);
    };

    self.cancelEdit = function() {
      self.editedMetadata(ko.mapping.fromJS(self.metadata));
      self.editable(false);
    };

    self.save = function() {
      self.metadata = ko.mapping.toJS(self.editedMetadata);
      self.editable(false);
    }
  };

  ko.components.register("metadata-editor", {
    viewModel: model,
    template: {element: "metadata-editor-template"}
  });

})();