LUPAPISTE.ForemanHistoryModel = function (params) {
  "use strict";
  var self = this;

  self.showCompleteForemanHistory = ko.observable(false);

  self.projects = ko.observableArray([]);

  ajax
    .query("reduced-foreman-history", {id: params.applicationId})
    .success(function (data) {
      self.projects(data.projects);
    })
    .call();

  self.followAppLink = function(project) {
    window.location.hash = "!/application/" + project.linkedAppId;
  };

  self.showAllProjects = function() {
    hub.send("show-dialog", { contentName: "foreman-history", contentParams: params });
  };
};

ko.components.register("foreman-history", {
  viewModel: LUPAPISTE.ForemanHistoryModel,
  template: { element: "foreman-history"}
});
