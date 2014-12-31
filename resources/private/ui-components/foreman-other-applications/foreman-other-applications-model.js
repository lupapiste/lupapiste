LUPAPISTE.ForemanOtherApplicationsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  console.log("other apps params", self.params);
  console.log("val", _.values(self.params.model));

  self.items = ko.observableArray();

  var models = _(self.params.model).values().filter(_.isObject).value();
  if (_.isEmpty(models)) {
    self.items([undefined]);
  } else {
    self.items(models);
  }

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
