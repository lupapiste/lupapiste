LUPAPISTE.ForemanModel = function() {
  "use strict";
  var self = this;

  self.application = null;
  self.email = ko.observable();
  self.error = ko.observable();
  self.processing = ko.observable();
  self.pending = ko.observable();
  self.disabled = ko.computed(function() {
    return self.email() && !util.isValidEmailAddress(self.email());
  });
  self.foremanApplications = ko.observableArray();
  self.foremanTasks = ko.observableArray();
  self.finished = ko.observable(false);
  self.foremanRoles = ko.observable(LUPAPISTE.config.foremanRoles);
  self.selectedRole = ko.observable();
  self.taskId = ko.observable();

  self.refresh = function(application) {
    function loadForemanApplications(id) {
      self.foremanApplications([]);
      ajax
        .query("foreman-applications", {id: id})
        .success(function(data) {
          var foremanTasks = _.where(self.application.tasks, { "schema-info": { "name": "task-vaadittu-tyonjohtaja" } });
          var foremans = [];

          _.forEach(data.applications, function(app) {
            var foreman = _.find(app.auth, {"role": "foreman"});
            var foremanDoc = _.find(app.documents, { "schema-info": { "name": "tyonjohtaja" } });
            var name = foremanDoc ? foremanDoc.data.kuntaRoolikoodi ? foremanDoc.data.kuntaRoolikoodi.value : undefined : undefined;
            var existingTask = _.find(foremanTasks, { "data": {"asiointitunnus": { "value": app.id } } });

            if (existingTask) {
              name = existingTask.taskname;
              foremanTasks = _.without(foremanTasks, existingTask);
            }

            var data = {"state": app.state,
                        "id": app.id,
                        "email": foreman ? foreman.username : undefined,
                        "firstName": foreman ? foreman.firstName : undefined,
                        "lastName": foreman ? foreman.lastName : undefined,
                        "name": name,
                        "statusName": app.state === "verdictGiven" ? "ok" : "new" };

            self.foremanApplications.push(data);
            foremans.push(data);
          });

          _.forEach(foremanTasks, function(task) {
            var data = { "name": task.taskname,
                         "taskId": task.id,
                         "statusName": "missing" };
            foremans.push(data);
          });

          self.foremanTasks({ "name": loc(["task-vaadittu-tyonjohtaja", "_group_label"]),
                              "foremans": foremans });
        })
        .error(
          // invited foreman can't always fetch applicants other foreman appications (if they are not invited to them also)
        )
        .call();
    }

    self.application = application;

    _.defer(function() {
      loadForemanApplications(application.id);
    });
  };

  self.inviteForeman = function(taskId) {
    console.log("taskId", taskId);
    self.taskId(taskId);
    self.email(undefined);
    self.finished(false);
    LUPAPISTE.ModalDialog.open("#dialog-invite-foreman");
  };

  self.openApplication = function(id) {
    window.location.hash = "!/application/" + id;
  };

  self.submit = function() {
    self.error(undefined);

    function inviteToApplication(id, cb, errorCb) {
      if (!errorCb) {
        errorCb = cb;
      }
      ajax.command("invite-with-role", { id: id,
                               documentName: "",
                               documentId: "",
                               path: "",
                               email: self.email(),
                               title: "",
                               text: "",
                               role: "foreman" })
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          cb(data);
        })
        .error(function(err) {
          errorCb(err);
        })
        .call();
    }

    function createApplication() {
      // 2. create "tyonjohtajan ilmoitus" application
      ajax.command("create-foreman-application", { id: self.application.id,
                                                   taskId: self.taskId() ? self.taskId() : "" })
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          // 3. invite foreman to new application
          if (self.email()) {
            inviteToApplication(data.id, function() {
              self.finished(data.id);
            }, function(err) {
              self.error(err.text);
            });
          } else {
            self.finished(data.id);
          }
        })
        .error(function(err) {
          self.error(err.text);
        })
        .call();
    }

    // 1. invite foreman to current application
    if (self.email()) {
      inviteToApplication(self.application.id, createApplication);
    } else {
      createApplication();
    }
    return false;
  };

  hub.subscribe({type: "dialog-close", id: "dialog-invite-foreman"}, function() {
    if (self.application && self.finished()) {
      repository.load(self.application.id);
    }
  });
};
