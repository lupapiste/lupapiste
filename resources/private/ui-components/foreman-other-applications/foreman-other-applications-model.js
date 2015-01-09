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

  var models = _.values(self.params.model);
  var index = 0;
  _.forEach(models, function(model) {
    self.items.push(createRow(model, index));
    index += 1;
  });

  hub.subscribe("hetuChanged", function(data) {
    // TODO fetch foreman other applications when hetu changes
  });

  self.addRow = function() {
    self.items.push(createRow(undefined, self.items().length));
  };

  self.removeRow = function() {
    // TODO remove row
  };
};

ko.components.register("foreman-other-applications", {
  viewModel: LUPAPISTE.ForemanOtherApplicationsModel,
  template: { element: "foreman-other-applications" }
});
