LUPAPISTE.ForemanOtherApplicationsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  self.items = ko.observableArray();

  var body = self.params.subSchema.body;
  var models = _.values(self.params.model);
  var index = 0;
  _.forEach(models, function(model) {
    index += 1;
    var row = [];
    _.forEach(body, function(subSchema) {
      var item = {
        applicationId: self.params.applicationId,
        path: self.params.path,
        index: index,
        schema: subSchema,
        model: model[subSchema.name]
      };
      row.push(item);
    });
    self.items.push(row);
  });

  hub.subscribe("hetuChanged", function(data) {
    // TODO fetch foreman other applications when hetu changes
  });

  self.addRow = function() {
    self.items.push(undefined);
  };

};

ko.components.register("foreman-other-applications", {
  viewModel: LUPAPISTE.ForemanOtherApplicationsModel,
  template: { element: "foreman-other-applications" }
});
