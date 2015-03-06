LUPAPISTE.ForemanModel = function() {
  "use strict";
  var self = this;

  self.application = ko.observable();
  self.inPostVerdictState = ko.pureComputed(function() {
    var app = ko.unwrap(self.application);
    return app ? app.inPostVerdictState : false;
  });
  self.email = ko.observable();
  self.error = ko.observable();
  self.processing = ko.observable();
  self.pending = ko.observable();
  self.disabled = ko.computed(function() {
    return self.email() && !util.isValidEmailAddress(self.email());
  });
  self.foremanApplications = ko.observableArray();
  self.selectedForeman = ko.observable();
  self.foremanTasks = ko.observableArray([]);
  self.finished = ko.observable(false);
  self.foremanRoles = ko.observable(LUPAPISTE.config.foremanRoles);
  self.selectedRole = ko.observable();
  self.taskId = ko.observable();
  self.isVisible = ko.computed(function() {
    return util.getIn(self, ["application", "permitType"]) === "R" &&
      !/tyonjohtajan-nimeaminen/.test(util.getIn(self, ["application", "operations", 0, "name"]));
  });
  self.linkedForemanApps = ko.observableArray();
  self.selectableForemen = ko.pureComputed(function() {
    return _.filter(self.foremanApplications(), function(app) {
      return !_.contains(self.linkedForemanApps(), app.id);
    });
  });

  self.selectedForeman.subscribe(function(val) {
    console.log("selectedForeman in foreman model", val);
  });

  self.refresh = function(application) {
    function foremanApplications(applications) {
      _.forEach(applications, function(app) {
        var foreman = _.find(app.auth, {"role": "foreman"});
        var foremanDoc = _.find(app.documents, { "schema-info": { "name": "tyonjohtaja-v2" } });
        var name = util.getIn(foremanDoc, ["data", "kuntaRoolikoodi", "value"]);

        var username  = util.getIn(foremanDoc, ["data", "yhteystiedot", "email", "value"]);
        var firstname = util.getIn(foremanDoc, ["data", "henkilotiedot", "etunimi", "value"]);
        var lastname  = util.getIn(foremanDoc, ["data", "henkilotiedot", "sukunimi", "value"]);

        if (!(username || firstname || lastname)) {
          username = util.getIn(foreman, ["username"]);
          firstname = util.getIn(foreman, ["firstName"]);
          lastname = util.getIn(foreman, ["lastName"]);
        }

        var data = {"state":      app.state,
                    "id":         app.id,
                    "email":      username,
                    "firstName":  firstname,
                    "lastName":   lastname,
                    "name":       name,
                    "statusName": app.state === "verdictGiven" ? "ok" : "new"};

        data.displayName = ko.pureComputed(function() {
          var output = "";
          var name = data.firstName ? data.firstName : ""
          name += data.lastName ? " " + data.lastName : "";
          output = name;
          if (_.isEmpty(output)) {
            output = data.id;
          }
          if (data.name) {
            output += ' (' + loc(['osapuoli.tyonjohtaja.kuntaRoolikoodi', data.name]) + ')';
          }
          return output;
        });

        self.foremanApplications.push(data);
      });
    }

    function loadForemanTasks() {
      var foremanTasks = _.where(self.application().tasks, { "schema-info": { "name": "task-vaadittu-tyonjohtaja" } });
      var foremen = [];
      var asiointitunnukset = [];

      _.forEach(foremanTasks, function(task) {
        var asiointitunnus = util.getIn(task, ["data", "asiointitunnus", "value"]);
        if (asiointitunnus) {
          asiointitunnukset.push(asiointitunnus);
        }
      });

      self.linkedForemanApps(asiointitunnukset);
      _.forEach(foremanTasks, function(task) {
        var asiointitunnus = util.getIn(task, ["data", "asiointitunnus", "value"])
        var linkedForemanApp = _.findWhere(self.foremanApplications(), { 'id': asiointitunnus});

        var data = { "name": task.taskname,
                     "taskId": task.id,
                     "statusName": linkedForemanApp ? linkedForemanApp.statusName : "missing",
                     "selectedForeman": ko.observable(_.isEmpty(asiointitunnus) ? undefined : asiointitunnus),
                     "selectableForemen": ko.observableArray()};

        data.selectableForemen(_.filter(self.foremanApplications(), function(app) {
          return !_.contains(asiointitunnukset, app.id) || app.id === asiointitunnus;
        }));

        data.selectedForeman.subscribe(function(val) {
          ajax
            .command("link-foreman-task", { id: self.application().id,
                                            taskId: data.taskId,
                                            foremanAppId: val ? val : ""})
            .success(function(data) {
              // tallennettu-indikaattori
              self.finished(true);
              repository.load(self.application().id);
            })
            .error(function() {
              console.log("error");
            })
            .call();
        });

        foremen.push(data);
      });
      self.foremanTasks({ "name": loc(["task-vaadittu-tyonjohtaja", "_group_label"]),
                          "foremen": foremen });
    }

    function loadForemanApplications(id) {
      self.foremanApplications([]);
      ajax
        .query("foreman-applications", {id: id})
        .success(function(data) {
          foremanApplications(data.applications);
          loadForemanTasks();
        })
        .error(function() {
          // noop
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
      self.taskId(undefined);
      self.selectedRole(undefined);
    }
    self.email(undefined);
    self.finished(false);
    self.error(undefined);
    self.selectedForeman(undefined);
    LUPAPISTE.ModalDialog.open("#dialog-invite-foreman");
  };

  self.linkForeman = function(taskId, foremanId) {
    console.log("link foreman");
  }

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
      var hakijaDocs = _.where(self.application().documents, {"schema-info": {"name": "hakija"}});
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
          email: util.getIn(auth, ["username"])
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

    if (self.selectedForeman()) {
      ajax
        .command("link-foreman-task", { id: self.application().id,
                                        taskId: self.taskId() ? self.taskId() : "",
                                        foremanAppId: self.selectedForeman()})
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          // tallennettu-indikaattori
          self.finished(self.application().id);
        })
        .error(function() {
          console.log("error");
        })
        .call();

    } else {
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
    }
    return false;
  };

  hub.subscribe({type: "dialog-close", id: "dialog-invite-foreman"}, function() {
    if (self.application() && self.finished()) {
      repository.load(self.application().id);
    }
  });
};
