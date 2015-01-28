LUPAPISTE.ForemanHistoryModel = function (params) {
  "use strict";
  var self = this;

  self.showCompleteForemanHistory = ko.observable(false);

  self.params = params;
  console.log("params", params);
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
    var newParams = params;
    newParams.showAllProjects = true;
    hub.send("show-dialog", { contentName: "foreman-history", contentParams: newParams });
  };
};

ko.components.register("foreman-history", {
  viewModel: LUPAPISTE.ForemanHistoryModel,
  template: { element: "foreman-history"}
});
