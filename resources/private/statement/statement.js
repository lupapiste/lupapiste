var statement = (function() {
  "use strict";

  var applicationId = null;
  var statementId = null;
  var statementModel = new StatementModel();

  function StatementModel() {
    var self = this;

    self.application = ko.observable();
  }

  function refresh(application) {
    statementModel.application(ko.mapping.fromJS(application));
    console.log("refresh: ",application);
    console.log(statementModel.application().title());
  }

  repository.loaded(function(event) {
    var application = event.applicationDetails.application;
    if (applicationId === application.id) { refresh(application); }
  });

  hub.onPageChange("statement", function(e) {
    applicationId = e.pagePath[0];
    statementId = e.pagePath[1];
    repository.load(applicationId);
  });

  $(function() { ko.applyBindings({ statementModel: statementModel }, $("#statement")[0]); });

})();
