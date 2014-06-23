LUPAPISTE.verdictPageController = (function() {
  "use strict";

  var currentApplicationId = null;
  var currentApplication = null;
  var currentVerdictId = null;

  function VerdictEditModel() {
    var self = this;

    self.applicationTitle = ko.observable();

    self.processing = ko.observable();

    self.statuses = _.range(1,43); // 42 different values in verdict in krysp (verdict.clj)

    self.backendId = ko.observable();
    self.draft = ko.observable();
    self.status = ko.observable();
    self.name = ko.observable();
    self.text = ko.observable();
    self.agreement = ko.observable(false);
    self.section = ko.observable();
    self.given = ko.observable();
    self.official = ko.observable();

    self.taskGroups = ko.observable();

    self.refresh = function(application, verdictId) {

      self.applicationTitle(application.title);

      var verdict = _.find((application.verdicts || []), function (v) {return v.id === verdictId;});
      if (verdict) {
        var paatos = verdict.paatokset[0];
        var pk = paatos.poytakirjat[0];
        var dates = paatos.paivamaarat;

        self.backendId(verdict.kuntalupatunnus);
        self.draft(verdict.draft);
        self.status(pk.status);
        self.name(pk.paatoksentekija);
        self.given(dates.anto);
        self.official(dates.lainvoimainen);
        self.text(pk.paatos);
        self.agreement(verdict.sopimus ? true : false); // Boolean required for the checkbox to work correctly
        self.section(pk.pykala);
      } else {
        history.back();
        repository.load(application.id);
      }

      var tasks = _.filter(application.tasks || [], function(task) {
        return _.isEqual(task.source, {"type":"verdict", id: currentVerdictId});
      });

      var schemaInfos = _.reduce(tasks, function(m, task) {
        var info = task.schema.info;
        m[info.name] = info;
        return m;
      },{});

      var groups = _.groupBy(tasks, function(task) {return task.schema.info.name;});

      self.taskGroups(_(groups)
        .keys()
        .map(function(n) {
          return {
            name: loc([n, "_group_label"]),
            order: schemaInfos[n].order,
            tasks: _.map(groups[n], function(task) {
              task.displayName = taskUtil.shortDisplayName(task);
              task.deleteTask = function() {
                LUPAPISTE.ModalDialog.showDynamicYesNo(
                    loc("areyousure"),
                    loc("task.delete.confirm"),
                    {title: loc("yes"), fn: function() {
                      ajax
                        .command("delete-task", {id: currentApplicationId, taskId: task.id})
                        .success(function(){repository.load(currentApplicationId);})
                        .call();}},
                        {title: loc("no")});
                return false;
              };
              task.statusName = LUPAPISTE.statuses[task.state] || "unknown";
              return task;
            })};})
        .sortBy("order")
        .valueOf());
    };

    self.returnToApplication = function() {
      window.location.hash = "!/application/" + currentApplicationId + "/verdict";
    };

    self.save = function(onSuccess) {
      var givenMillis = new Date(self.given()).getTime();
      var officialMillis = new Date(self.official()).getTime();
      ajax
        .command("save-verdict-draft",
          {id: currentApplicationId, verdictId: currentVerdictId,
           backendId: self.backendId(), status: self.status(),
           name: self.name(), text: self.text(),
           section: self.section(),
           agreement: self.agreement() || false,
           given: givenMillis, official: officialMillis})
        .success(onSuccess)
        .processing(self.processing)
        .call();
      return false;
    };

    self.submit = function() {
      self.save(function() {
        repository.load(currentApplicationId);
        LUPAPISTE.ModalDialog.showDynamicOk(loc("form.saved"), loc("saved"));
      });
    };

    self.commandAndBack = function(cmd) {
      ajax
      .command(cmd, {id: currentApplicationId, verdictId: currentVerdictId})
      .success(function() {
        repository.load(currentApplicationId);
        window.location.hash = "!/application/" + currentApplicationId + "/verdict";
      })
      .processing(self.processing)
      .call();
    };

    self.publish = function() {
      self.save(_.partial(self.commandAndBack, "publish-verdict"));
    };

    self.deleteVerdict = function() {
      LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("areyousure.message"), {title: loc("yes"), fn: _.partial(self.commandAndBack, "delete-verdict")});
    };

    self.disabled = ko.computed(function() {
      return self.processing() || !(self.backendId() && self.status() && self.name() && self.given() && self.official());
    });
  }

  var verdictModel = new VerdictEditModel();
  var authorizationModel = authorization.create();
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({}, "muut.muu");
  var createTaskController = LUPAPISTE.createTaskController;
  var commentsModel = new comments.create(false, ["authority"]);

  function refresh(application, verdictId) {
    var target = {type: "verdict", id: verdictId};
    currentApplication = application;
    currentApplicationId = currentApplication.id;
    currentVerdictId = verdictId;

    authorizationModel.refresh(application);
    verdictModel.refresh(application, verdictId);
    attachmentsModel.refresh(application, target);
    createTaskController.reset(currentApplicationId, target);
    commentsModel.refresh(application, target);
  }

  repository.loaded(["verdict"], function(application) {
    if (currentApplicationId === application.id) {
      refresh(application, currentVerdictId);
    }
  });

  hub.onPageChange("verdict", function(e) {
    var applicationId = e.pagePath[0];
    var verdictId = e.pagePath[1];
    // Reload application only if needed
    if (currentApplicationId !== applicationId) {
      repository.load(applicationId);
    } else if (currentVerdictId !== verdictId){
      refresh(currentApplication, currentVerdictId);
    }
    currentApplicationId = applicationId;
    currentVerdictId = verdictId;
  });

  $(function() {
    $("#verdict").applyBindings({
      verdictModel: verdictModel,
      authorization: authorizationModel,
      attachmentsModel: attachmentsModel,
      createTask: createTaskController,
      commentsModel: commentsModel
    });
  });

  return {
    setApplicationModelAndVerdictId: refresh
  };

})();
