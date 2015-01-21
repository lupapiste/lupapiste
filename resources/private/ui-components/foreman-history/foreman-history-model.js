LUPAPISTE.ForemanHistoryModel = function (params) {
  "use strict";
  var self = this;

  self.projects = ko.observableArray([]);

  ajax
    .query("foreman-history", {id: params.applicationId})
    .success(function (data) {
      self.projects(data.projects);
    })
    .error(function (data) {
      // TODO: Error needs to be handled?
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
