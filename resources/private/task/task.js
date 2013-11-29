var taskPageController = (function() {
  "use strict";

  var applicationId = null;
  var application = null;
  var taskId = null;
  var task = ko.observable();

  var authorizationModel = authorization.create();
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({type: "task"});

  function refreshTask() {
    var t = _.find(application.tasks, function(task) {return task.id === taskId;});

    task(t);
  }

  function setApplication(app) {
    application = app;
    authorizationModel.refresh(application);
    attachmentsModel.refresh(application, {type: "task", id: taskId});
    refreshTask();
  }

  repository.loaded(["task"], function(app) {
    if (applicationId === app.id) {
      setApplication(app);
    }
  });

  hub.onPageChange("task", function(e) {
    applicationId = e.pagePath[0];
    taskId = e.pagePath[1];
    // Reload application only if needed
    if (!application || applicationId !== application.id) {
      repository.load(applicationId);
    } else {
      refreshTask();
    }
  });


  $(function() {
    $("#task").applyBindings({
      task: task,
      authorization: authorizationModel,
      attachmentsModel: attachmentsModel
    });
  });

  return {
    setApplication: setApplication
  };

})();
