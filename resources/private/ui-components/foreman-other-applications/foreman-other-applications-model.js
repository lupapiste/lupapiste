LUPAPISTE.ForemanOtherApplicationsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  self.items = ko.observableArray();

  var createRow = function(model, index) {
    var res = _.filter(self.params.validationErrors, function(errors) {
      return _.isEqual(errors.path[0], self.params.path[0]);
    });

    var row = [];
    _.forEach(self.params.subSchema.body, function(subSchema) {
      var item = {
        applicationId: self.params.applicationId,
        documentId: self.params.documentId,
        path: self.params.path,
        index: index,
        schema: subSchema,
        model: model ? model[subSchema.name] : undefined,
        validationErrors: res
      };
      row.push(item);
    });
    return row;
  };

  for (var key in self.params.model) {
    self.items.push(createRow(self.params.model[key], key));
  }

  hub.subscribe("hetuChanged", function(data) {
    // TODO fetch foreman other applications when hetu changes
  });

  self.addRow = function() {
    var lastItem = _.first(_.last(self.items()));
    var index = lastItem && lastItem.index ? parseInt(lastItem.index) + 1 : 0;
    self.items.push(createRow(undefined, index.toString()));
  };

  self.removeRow = function() {
    // TODO remove row
  };
};

ko.components.register("foreman-other-applications", {
  viewModel: LUPAPISTE.ForemanOtherApplicationsModel,
  template: { element: "foreman-other-applications" }
});
