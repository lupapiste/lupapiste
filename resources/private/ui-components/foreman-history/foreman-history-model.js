LUPAPISTE.ForemanHistoryModel = function (params) {
  "use strict";
  var self = this;

  self.showCompleteForemanHistory = ko.observable(false);

  self.params = params;
  self.projects = ko.observableArray([]);

  var endpoint = "reduced-foreman-history";
  if (self.params.showAllProjects) {
    endpoint = "foreman-history";
  }

  ajax
    .query(endpoint, {id: params.applicationId})
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
    hub.send("show-dialog", { titleLoc: "tyonjohtaja.historia.otsikko",
                              contentName: "foreman-history",
                              contentParams: newParams });
  };
};
