var taskUtil = (function() {
  "use strict";
  function getTaskName(task) {
    return task.taskname || loc([task.schema.info.name, "_group_label"]);
  }

  function shortDisplayName(task) {
    var displayName = getTaskName(task);
    var prefix = task.schema.info.i18nprefix;
    var path = task.schema.info.i18npath;
    if (path && path.length) {
      if (path[path.length - 1] !== "value") {
        path.push("value");
      }
      var displayNameData = util.getIn(task.data || {}, path);
      if (displayNameData) {
        var key = prefix ? prefix + "." + displayNameData : displayNameData;
        displayName = loc(key);
      }
    }
    return displayName;
  }

  function longDisplayName(task, application) {
    return application.address + ": " + getTaskName(task);
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
  var processing = ko.observable(false);
  var pending = ko.observable(false);
  var taskSubmitOk = ko.observable(false);

  var authorizationModel = lupapisteApp.models.applicationAuthModel;
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({type: "task"}, "muut.muu", true);

  function reload() {
    repository.load(currentApplicationId);
  }

  function returnToApplication() {
    reload();
    pageutil.openApplicationPage({id: currentApplicationId}, "tasks");
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
      .success(reload)
      .error(reload)
      .call();
    return false;
  }

  function sendTask() {
    ajax.command("send-task", { id: currentApplicationId, taskId: currentTaskId, lang: loc.getCurrentLanguage()})
      .pending(pending)
      .processing(processing)
      .success(function() {
        reload();
        LUPAPISTE.ModalDialog.showDynamicOk(loc("integration.title"), loc("integration.success"));
      })
      .error(function(e){
        reload();
        LUPAPISTE.showIntegrationError("integration.title", e.text, e.details);
      })
      .call();
  }

  /**
   * @param {Object} application  Keys: id, tasks, attachment
   * @param {String} taskId       Current task ID
   */
  function refresh(application, taskId) {
    currentApplicationId = application.id;
    currentTaskId = taskId;

    lupapisteApp.setTitle(lupapisteApp.models.application.title());

    attachmentsModel.refresh(application, {type: "task", id: currentTaskId});

    var t = _.find(application.tasks, function(task) {return task.id === currentTaskId;});

    if (t) {
      t.displayName = taskUtil.longDisplayName(t, application);
      t.applicationId = currentApplicationId;
      t.deleteTask = deleteTask;
      t.returnToApplication = returnToApplication;
      t.approve = _.partial(runTaskCommand, "approve-task");
      t.reject = _.partial(runTaskCommand, "reject-task");
      t.approvable = authorizationModel.ok("approve-task") && (t.state === "requires_user_action" || t.state === "requires_authority_action");
      t.rejectable = authorizationModel.ok("reject-task");
      t.sendTask = sendTask;
      t.statusName = LUPAPISTE.statuses[t.state] || "unknown";
      task(t);

      var requiredErrors = util.extractRequiredErrors([t.validationErrors]);
      taskSubmitOk(authorizationModel.ok("send-task") && (t.state === "sent" || t.state === "ok") && !requiredErrors.length);

      var options = {collection: "tasks", updateCommand: "update-task", validate: true};
      docgen.displayDocuments("#taskDocgen", application, [t], authorizationModel, options);

    } else {
      $("#taskDocgen").empty();
      task(null);
      error("Task not found", currentApplicationId, currentTaskId);
      notify.error(loc("error.dialog.title"), loc("error.task-not-found"));
    }
  }

  repository.loaded(["task"], function(app) {
    if (currentApplicationId === app.id) {
      refresh(app, currentTaskId);
    }
  });

  hub.onPageLoad("task", function(e) {
    var applicationId = e.pagePath[0];
    currentTaskId = e.pagePath[1];
    // Reload application only if needed
    if (currentApplicationId !== applicationId) {
      repository.load(applicationId);
    } else {
      lupapisteApp.setTitle(lupapisteApp.models.application.title());
    }
    currentApplicationId = applicationId;
  });

  hub.subscribe("update-task-success", function(e) {
    if (task() && currentApplicationId === e.appId && currentTaskId === e.documentId) {
      var requiredErrors = util.extractRequiredErrors([e.results]);
      taskSubmitOk(authorizationModel.ok("send-task") && (task().state === "sent" || task().state === "ok") && !requiredErrors.length);
    }
  });

  $(function() {
    $("#task").applyBindings({
      task: task,
      pending: pending,
      processing: processing,
      authorization: authorizationModel,
      attachmentsModel: attachmentsModel,
      taskSubmitOk: taskSubmitOk
    });
  });

  return {
    setApplicationModelAndTaskId: refresh
  };

})();
