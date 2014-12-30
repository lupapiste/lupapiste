LUPAPISTE.ForemanOtherApplicationsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  hub.subscribe("hetuChanged", function(data) {
    // TODO fetch foreman other applications when hetu changes
  });
};

ko.components.register("foreman-other-applications", {
  viewModel: LUPAPISTE.ForemanOtherApplicationsModel,
  template: { element: "foreman-other-applications" }
});
