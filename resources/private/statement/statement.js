(function() {
  "use strict";

  var applicationId = ko.observable();
  var statementId = ko.observable();

  var authorizationModel = authorization.create();

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
      statementId: statementId
    });
  });

})();
