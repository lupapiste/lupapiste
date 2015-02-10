LUPAPISTE.ForemanModel = function() {
  "use strict";
  var self = this;

  self.application = ko.observable();
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
  self.isVisible = ko.computed(function() {
    return util.getIn(self, ["application", "permitType"]) === "R" &&
      !/tyonjohtajan-nimeaminen/.test(util.getIn(self, ["application", "operations", 0, "name"]));
  });

  self.refresh = function(application) {
    function loadForemanApplications(id) {
      self.foremanApplications([]);
      ajax
        .query("foreman-applications", {id: id})
        .success(function(data) {
          // TODO query only foreman tasks
          var foremanTasks = _.where(self.application().tasks, { "schema-info": { "name": "task-vaadittu-tyonjohtaja" } });
          var foremans = [];

          _.forEach(data.applications, function(app) {
            var foreman = _.find(app.auth, {"role": "foreman"});
            var foremanDoc = _.find(app.documents, { "schema-info": { "name": "tyonjohtaja-v2" } });
            var name = util.getIn(foremanDoc, ["data", "kuntaRoolikoodi", "value"]);
            var existingTask = _.find(foremanTasks, { "data": {"asiointitunnus": { "value": app.id } } });

            if (existingTask) {
              name = existingTask.taskname;
              foremanTasks = _.without(foremanTasks, existingTask);
            }

            var username  = util.getIn(foremanDoc, ["data", "yhteystiedot", "email", "value"]);
            var firstname = util.getIn(foremanDoc, ["data", "henkilotiedot", "etunimi", "value"]);
            var lastname  = util.getIn(foremanDoc, ["data", "henkilotiedot", "sukunimi", "value"]);

            if (!(username || firstname || lastname)) {
              username = util.getIn(foreman, ["username"]);
              firstname = util.getIn(foreman, ["firstName"]);
              lastname = util.getIn(foreman, ["lastName"]);
            }

            var data = {"state": app.state,
                        "id": app.id,
                        "email":     username,
                        "firstName": firstname,
                        "lastName":  lastname,
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
        .error(function() {
          // invited foreman can't always fetch applicants other foreman appications (if they are not invited to them also)
        })
        .call();
    }

    self.application(application);

    _.defer(function() {
      loadForemanApplications(application.id);
    });
  };

  self.inviteForeman = function(taskId) {
    if (_.isString(taskId)) {
      self.taskId(taskId);
      var foremanTask = _.find(self.application().tasks, { "id": taskId });
      if (foremanTask && foremanTask.taskname) {
        self.selectedRole(foremanTask.taskname.toLowerCase());
      } else {
        self.selectedRole(undefined);
      }
    } else {
      self.selectedRole(undefined);
    }
    self.email(undefined);
    self.finished(false);
    self.error(undefined);
    LUPAPISTE.ModalDialog.open("#dialog-invite-foreman");
  };

  self.openApplication = function(id) {
    window.location.hash = "!/application/" + id;
  };

  self.submit = function() {
    self.error(undefined);

    function inviteToApplication(params, cb) {
      var defaults = {
        id: "",
        documentName: "",
        documentId: "",
        path: "",
        email: "",
        title: "",
        text: "",
        role: ""
      };
      params = _.merge(defaults, params);

      ajax.command("invite-with-role", params)
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          cb(data);
        })
        .error(function(err) {
          // recipient might have already been invited
          error("Unable to invite user:", params.email, err);
          cb(err);
        })
        .call();
    }

    function inviteHakijat(id) {
      function doInvite(response) {
        if (response.ok) {
          var hakijaDocs = _.where(response.application.documents, {"schema-info": {"name": "hakija"}});
          var hakijat = _.map(hakijaDocs, function(doc) {
            var userId = util.getIn(doc, ["data", "henkilo", "userId", "value"]);
            var auth = _.find(self.application().auth, function(a) {
              return a.id === userId;
            });
            return {
              userId: userId,
              docId: doc.id,
              docName: util.getIn(doc, ["schema-info", "name"]),
              path: "henkilo",
              email: auth.username
            };
          });
          var deferreds = [];
          _.forEach(hakijat, function(hakija) {
            if (hakija.email) {
              deferreds.push(inviteToApplication({
                id: id,
                documentId: hakija.docId,
                documentName: hakija.docName,
                path: hakija.path,
                email: hakija.email,
                role: "writer"
              }, function(){}));
            }
          });
          $.when.apply($, deferreds)
          .then(function() {
            self.finished(id);
          });
        }
      }

      ajax.query("application", {id: id})
      .success(doInvite)
      .error(function(err) {
        error("Unable to load foreman application:", id, err);
      })
      .call();
    }

    function createApplication() {
      // 2. create "tyonjohtajan ilmoitus" application
      ajax.command("create-foreman-application", { "id": self.application().id,
                                                   "taskId": self.taskId() ? self.taskId() : "",
                                                   "foremanRole": self.selectedRole() ? self.selectedRole() : "" })
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          // 3. invite foreman to new application
          if (self.email()) {
            inviteToApplication({
                id: data.id,
                email: self.email(),
                role: "foreman"
              }, function() {
                inviteHakijat(data.id);
            });
          } else {
            inviteHakijat(data.id);
          }
        })
        .error(function(err) {
          self.error(err.text);
        })
        .call();
    }

    // 1. invite foreman to current application
    if (self.email()) {
      inviteToApplication({
                id: self.application().id,
                email: self.email(),
                role: "foreman"
              }, createApplication);
    } else {
      createApplication();
    }
    return false;
  };

  hub.subscribe({type: "dialog-close", id: "dialog-invite-foreman"}, function() {
    if (self.application() && self.finished()) {
      repository.load(self.application().id);
    }
  });
};
