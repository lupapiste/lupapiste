var taskUtil = (function() {

  function shortDisplayName(task) {
    var displayName = task.taskname;
    var prefix = task.schema.info.i18nprefix;
    var path = task.schema.info.i18npath;
    if (path && path.length) {
      if (path[path.length - 1] !== "value") path.push("value");
      var displayNameData = util.getIn(task.data || {}, path);
      if (displayNameData) {
        var key = prefix ? prefix + "." + displayNameData : displayNameData;
        displayName = loc(key);
      }
    }
    return displayName;
  }

  function longDisplayName(task, application) {
    return application.address + ": " + shortDisplayName(task);
  }

  return {
    shortDisplayName: shortDisplayName,
    longDisplayName: longDisplayName
  };
})();

var taskPageController = (function() {
  "use strict";

  var currentApplicationId = null;
  var currentTaskId = null;
  var task = ko.observable();

  var authorizationModel = authorization.create();
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({type: "task"});

  function deleteTask() {
    ajax
      .query("ping", {id: currentApplicationId, taskId: currentTaskId})
      .success(function() {
        // We're assuming that the application reloads when hash changes back to application
        window.location.hash = "!/application/" + currentApplicationId + "/tasks";
      })
      .call();
    return false;
  }

  function refresh(application, taskId) {
console.log("Refresh application", application.id, taskId);
    currentApplicationId = application.id;
    currentTaskId = taskId;

    authorizationModel.refresh(application);
    attachmentsModel.refresh(application, {type: "task", id: currentTaskId});

    var t = _.find(application.tasks, function(task) {return task.id === currentTaskId;});
    t.displayName = taskUtil.longDisplayName(t, application);
    t.applicationId = currentApplicationId;
    t.deleteTask = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("task.delete.confirm"),
      {title: loc("yes"), fn: deleteTask},
      {title: loc("no")}
    );
    return false;
    };
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
