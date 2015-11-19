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

  self.showAllProjects = function() {
    hub.send("show-dialog", { ltitle: "tyonjohtaja.historia.otsikko",
                              component: "foreman-history",
                              componentParams: _.defaults({showAllProjects: true}, params)});
  };
};
