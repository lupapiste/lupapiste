LUPAPISTE.ForemanOtherApplicationsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  self.rows = ko.observableArray();
  self.autoupdatedRows = ko.observableArray();

  var createRow = function(model, index) {
    var res = _.filter(self.params.validationErrors, function(errors) {
      return _.isEqual(errors.path[0], self.params.path[0]);
    });

    var row = [];
    _.forEach(self.params.subSchema.body, function(subSchema) {
      var rowModel = model ? model[subSchema.name] : undefined;
      var readonly = false;
      var readonlyFields = ["luvanNumero", "katuosoite", "rakennustoimenpide", "kokonaisala"];
      if (_.contains(readonlyFields, subSchema.name)) {
        readonly = util.getIn(model, ["autoupdated", "value"]) || false;
      }
      var item = {
        applicationId: self.params.applicationId,
        documentId: self.params.documentId,
        path: self.params.path,
        index: index,
        schema: subSchema,
        model: rowModel,
        validationErrors: res,
        readonly: readonly
      };
      row.push(item);
    });
    return row;
  };

  for (var key in self.params.model) {
    var model = self.params.model[key];
    if (util.getIn(model, ["autoupdated", "value"])) {
      self.autoupdatedRows.push(createRow(model, key));
    } else {
      self.rows.push(createRow(model, key));
    }
  }

  hub.subscribe("hetuChanged", function(data) {
    ajax.command("update-foreman-other-applications", {id: self.params.applicationId, foremanHetu: data.value})
    .success(function() {
      repository.load(self.params.applicationId);
    })
    .call();
  });

  self.addRow = function() {
    var lastItem = _.first(_.last(self.rows()));
    // If there are no rows added by user try calculate index from auto inserted rows
    if (_.isEmpty(self.rows())) {
      lastItem = _.first(_.last(self.autoupdatedRows()));
    }
    var index = lastItem && lastItem.index ? parseInt(lastItem.index) + 1 : 0;
    self.rows.push(createRow(undefined, index.toString()));
  };

  self.removeRow = function() {
    // TODO remove row
  };
};

ko.components.register("foreman-other-applications", {
  viewModel: LUPAPISTE.ForemanOtherApplicationsModel,
  template: { element: "foreman-other-applications" }
});
