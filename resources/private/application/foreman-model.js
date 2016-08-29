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
  self.foremanTasks = ko.observableArray([]);
  self.finished = ko.observable(false);
  self.foremanRoles = ko.observable(LUPAPISTE.config.foremanRoles);
  self.selectedRole = ko.observable();
  self.taskId = ko.observable();
  self.isVisible = ko.computed(function() {
    return util.getIn(self, ["application", "permitType"]) === "R" &&
      !/tyonjohtajan-nimeaminen/.test(util.getIn(self, ["application", "primaryOperation", "name"]));
  });
  var linkedForemanApps = ko.observableArray();

  self.canInvite = ko.pureComputed(_.partial(lupapisteApp.models.applicationAuthModel.ok, "invite-with-role"));
  self.canSelect = ko.pureComputed(_.partial(lupapisteApp.models.applicationAuthModel.ok, "link-foreman-task"));
  self.indicator = ko.observable();

  self.refresh = function(application) {
    function foremanApplications(applications) {
      _.forEach(applications, function(app) {
        var foreman = _.find(app.auth, function(f) {
          return f.role === "foreman" || util.getIn(f, ["invite", "role"]) === "foreman";
        });

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

        // Foreman is deemed substitute if any of the subsitute name fields is filled.
        var isSubstitute = Boolean( _.find( ["Etunimi", "Sukunimi"],
                                            function( s ) {
                                              return _.trim( util.getIn( foremanDoc, ["data",
                                                                                      "sijaistus",
                                                                                      "sijaistettavaHlo" + s,
                                                                                      "value"]));
                                            }));
        var data = {"state":       app.state,
                    "id":          app.id,
                    "email":       username,
                    "firstName":   firstname,
                    "lastName":    lastname,
                    "name":        name,
                    "statusName":  app.state === "acknowledged" || _.includes(LUPAPISTE.config.postVerdictStates, app.state) ? "ok" : "new",
                    "displayRole": name ? loc(["osapuoli.tyonjohtaja.kuntaRoolikoodi", name]) : "",
                    isSubstitute:  isSubstitute};

        data.displayName = ko.pureComputed(function() {
          var output = data.id;
          if (data.firstName || data.lastName) {
            output += " ";
            output += data.lastName ? data.lastName : "";
            if (data.lastName) {
              output += " ";
            }
            output += data.firstName ? data.firstName : "";
          }
          if (data.displayRole) {
            output += " (" + data.displayRole + ")";
          }
          return output;
        });
        self.foremanApplications.push(data);
      });
    }

    function loadForemanTasks() {
      var foremanTasks = _.filter(self.application().tasks, { "schema-info": { "name": "task-vaadittu-tyonjohtaja" } });
      var asiointitunnukset = _(foremanTasks)
        .map(function(task) {
          return util.getIn(task, ["data", "asiointitunnus", "value"]);
        })
        .filter()
        .value();

      linkedForemanApps(asiointitunnukset);

      var foremen = _.map(foremanTasks, function(task) {
        var asiointitunnus = util.getIn(task, ["data", "asiointitunnus", "value"]);
        var selectedForeman = ko.observable(_.isEmpty(asiointitunnus) ? undefined : asiointitunnus);
        var statusName = ko.pureComputed(function() {
          var linkedForemanApp = _.find(self.foremanApplications(), { "id": selectedForeman()});
          return linkedForemanApp ? linkedForemanApp.statusName : "missing";
        });
        var selectableForemen = ko.pureComputed(function() {
          return _.filter(self.foremanApplications(), function(app) {
            return !_.includes(linkedForemanApps(), app.id) || app.id === selectedForeman();
          });
        });

        return { name: task.taskname,
                 taskId: task.id,
                 statusName: statusName,
                 selectedForeman: selectedForeman,
                 selectableForemen: selectableForemen,
                 canInvite: self.canInvite,
                 selectEnabled: self.canSelect,
                 indicator: ko.observable() };
      });

      _.forEach(foremen, function(data) {
        data.selectedForeman.subscribe(function(val) {
          linkedForemanApps(_(foremen).invokeMap("selectedForeman").filter().value());
          ajax
            .command("link-foreman-task", { id: self.application().id,
                                            taskId: data.taskId,
                                            foremanAppId: val ? val : ""})
            .success(function() { 
              self.indicator({type: "saved"});
            })
            .error(function(err) {
              data.selectedForeman(undefined);
              hub.send("indicator", {style: "negative", message: err.text});
              self.error(err.text);
            })
            .call();
        });
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
        .call();
    }

    self.application(application);

    if (self.isVisible()) {
      _.defer(function() {
        loadForemanApplications(application.id);
      });
    }
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
    LUPAPISTE.ModalDialog.open("#dialog-invite-foreman");
  };

  self.openApplication = function(id) {
    pageutil.openApplicationPage({id:id});
  };

  self.submit = function() {
    self.error(undefined);
    ajax.command("create-foreman-application", { "id": self.application().id,
                                                 "taskId": self.taskId() ? self.taskId() : "",
                                                 "foremanRole": self.selectedRole() ? self.selectedRole() : "",
                                                 "foremanEmail": self.email() ? self.email() : "" })
      .processing(self.processing)
      .pending(self.pending)
      .success(function(data) {
        self.finished(data.id);
      })
      .error(function(err) {
        self.error(err.text);
      })
      .call();

    return false;
  };

  hub.subscribe({eventType: "dialog-close", id: "dialog-invite-foreman"}, function() {
    if (self.application() && self.finished()) {
      repository.load(self.application().id);
    }
  });
};
