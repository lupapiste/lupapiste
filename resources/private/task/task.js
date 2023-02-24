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

  var applicationModel = lupapisteApp.models.application;
  var authorizationModel = authorization.create();
  var taskAuthModel = authorization.create();

  var currentTaskId = null;
  var task = ko.observable();
  var processing = ko.observable(false);
  var pending = ko.observable(false);
  var service = lupapisteApp.services.documentDataService;

  var validationErrors = ko.computed(function() {
    var t = task();
    if (t && t.addedToService()) {
      var results = [service.findDocumentById(t.id).validationResults()];
      return _.concat( util.extractRequiredErrors(results),
                       util.extractWarnErrors( results ));
    }
  });

  var reviewSubmitOk = ko.computed(function() {
    return authorizationModel.ok("review-done") && _.isEmpty(validationErrors());
  });

  var attachmentsModel = {defaults: {target: ko.observable( {type: "task"})},
                          typeGroups: ko.pureComputed( function() {
                            var isKatselmus = String(_.get(task(), "schema-info.name")).indexOf("katselmus") > -1;
                            return isKatselmus ?
                              ["katselmukset_ja_tarkastukset"] :
                              ["suunnitelmat", "erityissuunnitelmat",
                               "pelastusviranomaiselle_esitettavat_suunnitelmat",
                               "selvitykset",
                               "yleiset-alueet"];
                          }),
                          dropZoneSectionId: "task",
                          canAdd: ko.pureComputed( function() {
                            return "sent" !== _.get(task(), "state");
                          })};

  function returnToApplication() {
    applicationModel.lightReload();
    applicationModel.open("tasks");
  }

  function deleteTask() {
    hub.send("show-dialog",
             {ltitle: "areyousure",
              size: "medium",
              component: "yes-no-dialog",
              componentParams: {ltext: "task.delete.confirm",
                                yesFn: function() {
                                  ajax
                                    .command("delete-task", {id: applicationModel.id(),
                                                             taskId: currentTaskId})
                                    .success(returnToApplication)
                                    .call();},
                                lyesTitle: "yes",
                                lnoTitle: "no"}});
    return false;
  }

  function runTaskCommand(cmd) {
    ajax.command(cmd, { id: applicationModel.id(), taskId: currentTaskId})
      .success(applicationModel.lightReload)
      .error(applicationModel.lightReload)
      .call();
    return false;
  }

  function showIntegrationError( response ) {
    util.showIntegrationError( {ltext: response.text,
                                details: response.details} );
  }

  function reviewDoneAjax() {
    ajax.command("review-done", { id: applicationModel.id(), taskId: currentTaskId, lang: loc.getCurrentLanguage()})
      .pending(pending)
      .processing(processing)
      .success(function(resp) {
        var permit = externalApiTools.toExternalPermit(applicationModel._js);
        applicationModel.lightReload();

        if (!resp.integrationAvailable) {
          hub.send("show-dialog", {ltitle: "integration.title",
                                   size: "medium",
                                   component: "ok-dialog",
                                   componentParams: {ltext: "integration.unavailable"}});
        } else {
          LUPAPISTE.ModalDialog.showDynamicOk(loc("integration.title"), loc("integration.success"));
          if (applicationModel.externalApi.enabled()) {
            hub.send("external-api::integration-sent", permit);
          }
        }
      })
      .onError("error.invalid-task-type", notify.ajaxError)
      .error(function(e){
        applicationModel.lightReload();
        showIntegrationError( e );
      })
      .call();
  }

  function markFaultyAjax( notes ) {
    ajax.command("mark-review-faulty", { id: applicationModel.id(),
                                         taskId: currentTaskId,
                                       notes:  notes})
      .pending(pending)
      .processing(processing)
      .success(function() {
        applicationModel.lightReload();
      })
      .onError("error.invalid-task-type", notify.ajaxError)
      .call();
  }

  function resendReview() {
    ajax.command("resend-review-to-backing-system", { id: applicationModel.id(), taskId: currentTaskId, lang: loc.getCurrentLanguage() })
      .pending(pending)
      .processing(processing)
      .success(function(resp) {
        applicationModel.lightReload();

        if (!resp.integrationAvailable) {
          hub.send("show-dialog", {ltitle: "integration.title",
                                   size: "medium",
                                   component: "ok-dialog",
                                   componentParams: {ltext: "integration.unavailable"}});
        } else {
          LUPAPISTE.ModalDialog.showDynamicOk(loc("integration.title"), loc("integration.success"));
        }
      })
      .onError("error.invalid-task-type", notify.ajaxError)
      .error(function(e){
        applicationModel.lightReload();
        showIntegrationError( e );
      })
      .call();
  }

  function reviewDone() {
    hub.send("show-dialog", {ltitle: "areyousure",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "areyousure.review-done",
                                               yesFn: reviewDoneAjax,
                                               lyesTitle: "yes",
                                               lnoTitle: "no"}});
  }

  function markFaulty() {
    hub.send("show-dialog",
             {ltitle: "areyousure",
              size: "medium",
              component: "mark-review-faulty-dialog",
              componentParams: {
                notes: service.getInDocument( currentTaskId,
                                              ["katselmus", "huomautukset", "kuvaus"])
                       .model(),
                yesFn: markFaultyAjax}});
  }

  /**
   * @param {Object} application  Keys: id, tasks, attachment
   * @param {String} taskId       Current task ID
   */
  function refresh(application, taskId) {
    docgen.clear("taskDocgen");
    task(null);

    currentTaskId = taskId;

    lupapisteApp.setTitle(applicationModel.title());

    attachmentsModel.defaults.target({type: "task", id: currentTaskId});

    var t = _.find(application.tasks, function(task) {return task.id === currentTaskId;});

    if (t) {
      // update-task command belongs to tasks category.
      authorization.refreshModelsForCategory( _.set( {}, taskId, taskAuthModel),
                                              application.id,
                                              "tasks",
                                              function() {
                                                authorizationModel.refresh(application, {taskId: taskId}, function() {
                                                  authorizationModel.setData( _.merge( authorizationModel.getData(),
                                                                                       _.pick( taskAuthModel.getData(),
                                                                                               ["update-task"])));
                                                  t.approvable = authorizationModel.ok("approve-task");
                                                  t.rejectable = authorizationModel.ok("reject-task");
                                                  t.isEndReview = authorizationModel.ok( "is-end-review");

                                                  t.displayName = taskUtil.longDisplayName(t, application);
                                                  t.applicationId = application.id;
                                                  t.deleteTask = deleteTask;
                                                  t.returnToApplication = returnToApplication;
                                                  t.approve = _.partial(runTaskCommand, "approve-task");
                                                  t.reject = _.partial(runTaskCommand, "reject-task");
                                                  t.reviewDone = reviewDone;
                                                  t.markFaulty = markFaulty;
                                                  t.resendReview = resendReview;
                                                  t.statusName = LUPAPISTE.statuses[t.state] || "unknown";
                                                  t.addedToService = ko.observable();
                                                  task(t);

                                                  service.addDocument(task());
                                                  t.addedToService( true );
                                                });
                                              });
    } else {
      error("Task not found", application.id, currentTaskId);
      notify.error(loc("error.dialog.title"), loc("error.task-not-found"));
    }
  }

  hub.subscribe("application-model-updated", function() {
    if (pageutil.getPage() === "task") {
      refresh(applicationModel._js, currentTaskId);
    }
  });

  hub.onPageLoad("task", function(e) {
    var applicationId = e.pagePath[0];
    var taskId = e.pagePath[1];
    // Reload application only if needed
    if (applicationModel.id() !== applicationId) {
      currentTaskId = taskId;
      repository.load(applicationId);
    } else if (taskId !== currentTaskId) {
      refresh(applicationModel._js, taskId);
    } else {
      lupapisteApp.setTitle(applicationModel.title());
    }
  });

  $(function() {
    $("#task").applyBindings({
      task: task,
      pending: pending,
      processing: processing,
      authorization: authorizationModel,
      attachmentsModel: attachmentsModel,
      dataService: service,
      reviewSubmitOk: reviewSubmitOk
    });
  });

  return {
    setApplicationModelAndTaskId: refresh
  };

})();
