LUPAPISTE.ForemanHistoryModel = function (params) {
  "use strict";
  var self = this;

  self.projects = ko.observableArray([]);

  ajax
    .query("foreman-history", {id: params.applicationId})
    .success(function (data) {
      self.projects(data.projects);
    })
    .call();

  self.followAppLink = function(project) {
    window.location.hash = "!/application/" + project.linkedAppId;
  };
};

ko.components.register("foreman-history", {
  viewModel: LUPAPISTE.ForemanHistoryModel,
  template: { element: "foreman-history"}
});
