var taskPageController = (function() {
  "use strict";

  var currentApplicationId = null;
  var application = null;
  var currentTaskId = null;
  var task = ko.observable();

  var authorizationModel = authorization.create();
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({type: "task"});


  function refresh(app, taskId) {
    if (typeof app === "function") {
      application = ko.toJS(app);
    } else {
      application = app;
    }
    console.log("Set application", application.id, taskId);
    currentApplicationId = application.id;
    currentTaskId = taskId;

    authorizationModel.refresh(application);
    attachmentsModel.refresh(application, {type: "task", id: currentTaskId});

    var t = _.find(application.tasks, function(task) {return task.id === currentTaskId;});
    task(t);
  }

  repository.loaded(["task"], function(app) {
    if (currentApplicationId === app.id) {
      refresh(app, currentTaskId);
    }
  });

  hub.onPageChange("task", function(e) {
    currentApplicationId = e.pagePath[0];
    currentTaskId = e.pagePath[1];
    // Reload application only if needed
    if (!application || currentApplicationId !== application.id) {
console.log("Load application");
      repository.load(currentApplicationId);
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
    setApplicationModelAndTaskId: refresh
  };

})();
