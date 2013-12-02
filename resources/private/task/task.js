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
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("areyousure"),
        loc("task.delete.confirm"),
          {title: loc("yes"), fn: function() {
            ajax
            .query("ping", {id: currentApplicationId, taskId: currentTaskId})
            .success(function() {
              repository.load(currentApplicationId);
              window.location.hash = "!/application/" + currentApplicationId + "/tasks";
            })
            .call();}},
            {title: loc("no")}
        );
    return false;
  }

  /**
   * @param {Object} application  Keys: id, tasks, attachment
   * @param {String} taskId       Current task ID
   */
  function refresh(application, taskId) {
    currentApplicationId = application.id;
    currentTaskId = taskId;

    attachmentsModel.refresh(application, {type: "task", id: currentTaskId});

    var taskContentContainer = $("#taskDocgen").empty();

    var t = _.find(application.tasks, function(task) {return task.id === currentTaskId;});
    if (!t) {
      task(null);
      error("Task not found", currentApplicationId, currentTaskId);
      notify.error(loc("error.dialog.title"), loc("error.task-not-found"));
    } else {
      t.displayName = taskUtil.longDisplayName(t, application);
      t.applicationId = currentApplicationId;
      t.deleteTask = deleteTask;
      task(t);

      authorizationModel.refreshWithCallback({id: currentApplicationId}, function() {
        docgen.displayDocuments("#taskDocgen", application, [t], authorizationModel, {collection: "tasks", updateCommand: "update-task"});
      });
    }
  }

  repository.loaded(["task"], function(app) {
    if (currentApplicationId === app.id) {
      refresh(app, currentTaskId);
    }
  });

  hub.onPageChange("task", function(e) {
    var applicationId = e.pagePath[0];
    currentTaskId = e.pagePath[1];
    // Reload application only if needed
    if (currentApplicationId !== applicationId) {
      repository.load(applicationId);
    }
    currentApplicationId = applicationId;
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
