(function() {
  "use strict";

  var makeSubObjectObservable = function(data) {
    return _.object(_.map(data, function(value, key) {
      if (_.isObject(value)) {
        value = ko.observable(value);
      }
      return [key, value];
    }));
  };

  var model = function(params) {
    var self = this;
    self.attachmentId = params.attachmentId;
    self.metadata = params.metadata();
    self.editable = ko.observable(false);
    self.editedMetadata = ko.observable(makeSubObjectObservable(self.metadata));
    self.schema = ko.observableArray();
    // It does not seem to be possible to use visible: !editable, so we need another observable for that.
    self.displayOnly = ko.pureComputed(function() {
      return !self.editable();
    });
    self.dependencies = ko.pureComputed(function() {
      var depMap = {};
      _.foreach(self.schema(), function(item) {
        if (item.dependencies) {
          depMap[item.type] = {};
          _.foreach(item.dependencies, function(val, key) {
            depMap[item.type][key] = val;
          });
        }
      });
      return depMap;
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
      self.editedMetadata(makeSubObjectObservable(self.metadata));
      self.editable(false);
    };

    self.save = function() {
      self.metadata = ko.mapping.toJS(self.editedMetadata);
      // The bindings in view-only mode won't update if editedMetadata is not set here again. Why?
      self.editedMetadata(makeSubObjectObservable(self.metadata));
      self.editable(false);
    }
  };

  ko.components.register("metadata-editor", {
    viewModel: model,
    template: {element: "metadata-editor-template"}
  });

})();