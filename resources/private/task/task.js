(function() {
  "use strict";

  var applicationId = null;

  var authorizationModel = authorization.create();
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({type: "task"}); // TODO ID?

  repository.loaded(["task"], function(application) {
    if (applicationId === application.id) {
      authorizationModel.refresh(application);
      attachmentsModel.refresh(application);
    }
  });

  hub.onPageChange("task", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });


  $(function() {
    $("#task").applyBindings({
      task: {},
      authorization: authorizationModel,
      attachmentsModel: attachmentsModel
    });
  });

})();
