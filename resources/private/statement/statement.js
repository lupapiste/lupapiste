(function() {
  "use strict";

  var applicationId = ko.observable();
  var statementId = ko.observable();
  var submitAllowed = ko.observable(false);

  var authorizationModel = authorization.create();

  var tabs = ["statement"];
  var selectedTab = ko.observable("statement");

  hub.subscribe("statement::submitAllowed", function(data) {
    submitAllowed(data.value);
  });

  repository.loaded(["statement"], function(application) {
    if (applicationId() === application.id) {
      authorizationModel.refresh(application, {statementId: statementId()});
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
      applicationId: applicationId,
      statementId: statementId,
      submitAllowed: submitAllowed,
      selectedTab: selectedTab,
      tabs: tabs
    });
  });

})();
