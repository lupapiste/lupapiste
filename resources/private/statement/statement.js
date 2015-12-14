(function() {
  "use strict";

  var applicationId = ko.observable();
  var application = ko.observable();
  var statementId = ko.observable();

  var authorizationModel = authorization.create();

  var tabs = ["statement", "reply"];
  var selectedTab = ko.observable("statement");
  var submitAllowed = ko.observable({statement: false, reply: false});

  hub.subscribe("statement::submitAllowed", function(data) {
    submitAllowed(_.set(submitAllowed(), data.tab, data.value));
  });

  hub.subscribe("statement::refresh", function() {
    repository.load(applicationId());
  });

  repository.loaded(["statement"], function(app) {
    if (applicationId() === app.id) {
      application(app);
      authorizationModel.refresh(app, {statementId: statementId()});
    }
  });

  hub.onPageLoad("statement", function(e) {
    applicationId(e.pagePath[0]);
    statementId(e.pagePath[1]);
    repository.load(applicationId());
  });

  $(function() {
    $("#statement").applyBindings({
      authorization: authorizationModel,
      application: application,
      applicationId: applicationId,
      statementId: statementId,
      submitAllowed: submitAllowed,
      selectedTab: selectedTab,
      tabs: tabs
    });
  });

})();
