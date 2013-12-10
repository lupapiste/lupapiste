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
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({type: "task"}, "muut.muu", true);

  function returnToApplication() {
    repository.load(currentApplicationId);
    window.location.hash = "!/application/" + currentApplicationId + "/tasks";
  }

  function deleteTask() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("areyousure"),
        loc("task.delete.confirm"),
          {title: loc("yes"), fn: function() {
            ajax
            .command("delete-task", {id: currentApplicationId, taskId: currentTaskId})
            .success(returnToApplication)
            .call();}},
            {title: loc("no")}
        );
    return false;
  }

  function runTaskCommand(cmd) {
    var id = currentApplicationId;
    ajax.command(cmd, { id: id, taskId: currentTaskId})
      .success(function() {
        repository.load(id);
      })
      .error(function() {
        repository.load(id);
      })
      .call();
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

    var t = _.find(application.tasks, function(task) {return task.id === currentTaskId;});
    if (!t) {
      $("#taskDocgen").empty();
      task(null);
      error("Task not found", currentApplicationId, currentTaskId);
      notify.error(loc("error.dialog.title"), loc("error.task-not-found"));
    } else {
      t.displayName = taskUtil.longDisplayName(t, application);
      t.applicationId = currentApplicationId;
      t.deleteTask = deleteTask;
      t.returnToApplication = returnToApplication;
      t.approve = _.partial(runTaskCommand, "approve-task");
      t.reject = _.partial(runTaskCommand, "reject-task");
      authorizationModel.refreshWithCallback({id: currentApplicationId}, function() {
        t.approvable = authorizationModel.ok("approve-task") && (t.state === "requires_user_action" || t.state === "requires_authority_action");
        t.rejectable = authorizationModel.ok("reject-task") && (t.state === "requires_authority_action" || t.state === "ok");
        t.statusName = LUPAPISTE.statuses[t.state] || "unknown";
        task(t);
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
