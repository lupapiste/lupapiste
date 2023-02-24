LUPAPISTE.ForemanModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());
  var authorizationModel = lupapisteApp.models.applicationAuthModel;

  self.userEmail = lupapisteApp.models.currentUser.email;
  self.userIsAuthority = lupapisteApp.models.currentUser.isAuthority;
  self.userIsApplicant = self.disposedPureComputed(function() {
    if (_.isEmpty(self.application())) {
      return false;
    }
    var applicantDocs = _.filter(self.application().documents, function(doc) {
      return doc["schema-info"].subtype === "hakija";
    });

    var checkApplicantDoc = function(doc) {
      var person = doc.data._selected.value === "henkilo" ? doc.data.henkilo : doc.data.yritys.yhteyshenkilo;
      return person.yhteystiedot.email.value === self.userEmail()
        || (person.userId && person.userId.value === lupapisteApp.models.currentUser.id);
    };
    return _.some(applicantDocs, checkApplicantDoc);
  });

  self.application = ko.observable();
  self.inPostVerdictState = self.disposedPureComputed(function() {
    var app = ko.unwrap(self.application);
    return app ? app.inPostVerdictState : false;
  });

  // Utils for the inspection summary link on the termination dialog
  self.inspectionSummaryHTML = self.disposedPureComputed(function() {
    var hasInspectionSummaries = authorizationModel.ok("create-inspection-summary");
    var locKey = "foreman.terminate.reminder." + (hasInspectionSummaries ? "with-link" : "without-link");
    var onclick = "$('.dialog-close').trigger('click'); lupapisteApp.models.application.open('inspectionSummaries');";
    return loc(locKey, onclick);
  });

  self.email = ko.observable();
  self.error = ko.observable();
  self.processing = ko.observable();
  self.pending = ko.observable();
  self.foremanRows = ko.observableArray();
  self.finished = ko.observable(false);
  self.finished = ko.observable(false);
  self.selectedRole = ko.observable();
  self.selectedForTermination = ko.observable();
  self.terminationReason = ko.observable();
  self.taskId = ko.observable();
  self.inviteDisabled = ko.computed(function() {
    return _.isEmpty(self.selectedRole()) || (self.email() && !util.isValidEmailAddress(self.email()));
  });
  self.terminateDisabled = ko.computed(function() {
    return _.isEmpty(self.terminationReason());
  });
  self.isVisible = ko.pureComputed(function() {
    return util.getIn(self, ["application", "permitType"]) === "R" &&
       !/tyonjohtajan-nimeaminen|raktyo-aloit-loppuunsaat/.test(
         util.getIn(self, ["application", "primaryOperation", "name"]));
  });

  self.canInvite = ko.pureComputed(_.partial(lupapisteApp.models.applicationAuthModel.ok, "invite-with-role"));
  self.canSelect = ko.pureComputed(_.partial(lupapisteApp.models.applicationAuthModel.ok, "link-foreman-task"));
  self.indicator = ko.observable();

  var formatDate = function(timestamp) {
    if (_.isNumber(timestamp) || !_.isEmpty(timestamp)) {
      return util.finnishDate(timestamp);
    }
    return "";
  };

  self.refresh = function(application) {
    function updateForemanRows(applications) {
      _.forEach(applications, function(app) {
        var name, data;

        // A placeholder row for a required foreman role that doesn't have a linked foreman application yet
        if (app["unmet-role"]) {
          data = {
            displayRole:    app["role-name"],
            lupapisteRole:  app["lupapiste-role"],
            migrated:       app.migrated === true,
            statusName:     "required"
          };
        }

        // An actual foreman application (that may meet a requirement or not)
        else {
          var foreman = _.find(app.auth, function (f) {
            return f.role === "foreman" || util.getIn(f, ["invite", "role"]) === "foreman";
          });

          var foremanDoc = _.find(app.documents, {"schema-info": {"name": "tyonjohtaja-v2"}});
          name = util.getIn(foremanDoc, ["data", "kuntaRoolikoodi", "value"]);

          var username = util.getIn(foremanDoc, ["data", "yhteystiedot", "email", "value"]);
          var phone = util.getIn(foremanDoc, ["data", "yhteystiedot", "puhelin", "value"]);
          var firstname = util.getIn(foremanDoc, ["data", "henkilotiedot", "etunimi", "value"]);
          var lastname = util.getIn(foremanDoc, ["data", "henkilotiedot", "sukunimi", "value"]);

          if (!(username || firstname || lastname)) {
            username = util.getIn(foreman, ["username"]);
            firstname = util.getIn(foreman, ["firstName"]);
            lastname = util.getIn(foreman, ["lastName"]);
          }

          // Foreman substitute data
          var getSubstituteField = function (field) {
            return util.getIn(foremanDoc, ["data", "sijaistus", field, "value"]);
          };
          var getSubstituteName = function (s) {
            return _.trim(getSubstituteField("sijaistettavaHlo" + s));
          };
          var getSubstituteDate = function (s) {
            return formatDate(getSubstituteField(s));
          };

          var substituteFor = _.trim(getSubstituteName("Etunimi") + " " + getSubstituteName("Sukunimi"));
          var isSubstitute = !_.isEmpty(substituteFor);
          var substituteDateRange = getSubstituteDate("alkamisPvm") + " - " + getSubstituteDate("paattymisPvm");

          // Wrangling the responsibility start/end dates to more readable formats
          var dateRangeStatus = "";
          var dateRange = formatDate(app.started) + " - " + formatDate(app.ended);
          if (_.isNull(app.started) && _.isNull(app.ended)) {
            dateRange = "";
          } else {
            var dateRangeLoc = "not-requested";
            if (!_.isNull(app.ended)) {
              dateRangeLoc = "terminated";
            } else if (app["termination-requested"]) {
              dateRangeLoc = "requested";
            }
            dateRangeStatus = loc("foreman.termination.status." + dateRangeLoc);
          }

          // Format the request timestamp into a date with a clarifier
          var requestDate = "";
          if (app["termination-request-ts"]) {
            requestDate = loc("foreman.termination.status.requested") + " " + formatDate(app["termination-request-ts"]);
          }

          var requirementClass = loc(["osapuoli.patevyys-tyonjohtaja.patevyysvaatimusluokka",
            util.getIn(foremanDoc, ["data", "patevyysvaatimusluokka", "value"])]);

          // Foreman responsibilities
          var tasks = util.getIn(foremanDoc, ["data", "vastattavatTyotehtavat"]);
          var responsibilities = _(_.keys(tasks))
            .filter(function (key) {
              var includeOther = tasks.muuMika && tasks.muuMika.value;
              if (key === "muuMika" || (key === "muuMikaValue" && !includeOther)) {
                return false;
              }
              return tasks[key].value;
            })
            .map(function (key) {
              return key === "muuMikaValue" ? tasks[key].value : loc(["osapuoli.tyonjohtaja.vastattavatTyotehtavat", key]);
            })
            .value();

          // Icons
          var statusName = util.getIn(app, ["latest-verdict-status", "status"]);

          var hidden = app["_non-listed-foreman"] && statusName === "rejected";

          data = {
            state:                app.state,
            id:                   app.id,
            email:                username,
            phone:                util.formatPhoneNumber(phone),
            started:              formatDate(app.started),
            ended:                formatDate(app.ended),
            hidden:               hidden,
            fullName:             _.replace(firstname + " " + lastname, /undefined/g, "").trim(),
            name:                 name,
            statusName:           statusName,
            displayRole:          name ? loc(["osapuoli.tyonjohtaja.kuntaRoolikoodi", name]) : "",
            isSubstitute:         isSubstitute,
            substituteFor:        substituteFor,
            substituteDateRange:  substituteDateRange,
            terminationReason:    app["termination-reason"],
            terminationRequested: app["termination-requested"],
            dateRange:            dateRange,
            dateRangeStatus:      dateRangeStatus,
            requestDate:          requestDate,
            requirementClass:     requirementClass,
            responsibilities:     responsibilities.join(", ")
          };
        }

        // Push either foreman app or requirement to list used by table
        var icons = {
          "ok":       "lupicon-circle-check",
          "new":      "lupicon-circle-dash",
          "rejected": "lupicon-circle-remove",
          "required": "lupicon-circle-attention"
        };
        if (!_.isEmpty(data.id)) {
          data.text = data.displayRole
            + (_.isEmpty(data.requirementClass) ? ": " : " (" + data.requirementClass + "): ")
            + data.fullName
            + (_.isEmpty(data.phone) ? "" : " / " + data.phone);
        }

        data.icon = icons[data.statusName] + (data.migrated === true ? " migrated" : "");
        data.iconTooltip = loc("foreman.task." + data.statusName + ".label");
        data.permitCondition = loc(app.required ? "yes" : "no");
        data.terminatable = !data.terminationRequested && data.statusName === "ok" && !data.ended;
        self.foremanRows.push(data);
      });
    }

    function loadForemanRows(id) {
      self.foremanRows([]);
      ajax
        .query("foreman-applications", {id: id})
        .success(function(data) {
          updateForemanRows(data.applications);
        })
        .call();
    }

    self.application(application);

    if (self.isVisible()) {
      _.defer(function() {
        loadForemanRows(application.id);
      });
    }
  };

  self.sortedForemanRows = self.disposedComputed( function() {
    var order = lupapisteApp.services.taskService.foremanOrder();
    var rows = self.foremanRows();
    return _.sortBy( rows,
                     function( data ) {
                       var index = _.indexOf( order, _.toLower( data.displayRole ));
                       // Not found is last
                       return index < 0 ? _.size( rows ) : index;
                     });
  });

  self.openApplication = function(id) {
    pageutil.openApplicationPage({id:id});
  };

  // Select dropdown options
  var selectOptionsTextSuffix = " (" + _.lowerCase(loc("foreman.task.required.label")) + ")";
  self.foremanRoles = self.disposedPureComputed(function() {
    return _(LUPAPISTE.config.foremanRoles)
      .map(function(item) {
        item.localized = loc(item.i18nkey);
        item.required = _.some(self.foremanRows(), function(application) {
          return application.lupapisteRole === item.name && application.statusName === "required";
        });
        item.optionsText = item.localized + (item.required ? selectOptionsTextSuffix : "");

        // The object itself must change or knockout doesn't realize it has done so
        return _.assign({}, item);
      })
      .sortBy(function(item) {
        return (item.required ? "0" : "1") + item.localized; // Sort required up top
      })
      .value();
  });

  // Only the applicant, authority or the foreman themselves can request the termination of a given foreman
  self.canRequestTermination = function(foreman) {
    return foreman.terminatable &&
      (authorizationModel.ok("request-foreman-termination") || authorizationModel.ok("terminate-foreman")) &&
      (self.userIsAuthority() || foreman.email === self.userEmail() || self.userIsApplicant());
  };

  self.canApproveTermination = function(foreman) {
    return foreman.terminationRequested && authorizationModel.ok("confirm-foreman-termination");
  };

  // Authority can remove rejected foremen from the list to declutter it
  self.canRemoveListing = function(foreman) {
    return foreman.statusName === "rejected" && authorizationModel.ok("remove-foreman-from-list");
  };

  // Button functions
  self.inviteForeman = function(selected) {
    if (_.isString(selected)) {
      self.taskId(undefined);
      self.selectedRole(undefined);

      var foremanTask = _.find(self.application().tasks, function (data) {
        return _.get(data, "data.kuntaRoolikoodi.value") === selected;
      });
      if (foremanTask) {
        self.taskId(foremanTask.id);
        self.selectedRole(selected);
      }
    }

    self.email(undefined);
    self.finished(false);
    self.error(undefined);
    LUPAPISTE.ModalDialog.open("#dialog-invite-foreman");
  };

  self.requestTermination = function() {
    self.selectedForTermination(this);
    self.terminationReason("");
    self.error(undefined);
    LUPAPISTE.ModalDialog.open("#dialog-terminate-foreman");
  };

  var errorFn = function(err) {
    self.error(err.text);
  };

  self.approveTerminationRequest  = function() {
    var foreman = this;
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("foreman.terminate.confirm"),
      {title: loc("approve"), fn: function() { self.doApproveTerminationRequest(foreman); }},
      {title: loc("cancel")});
  };

  self.doApproveTerminationRequest = function(foreman) {
    self.error(undefined);
    var params = {"id":             self.application().id,
                  "foreman-app-id": foreman.id};
    ajax.command("confirm-foreman-termination", params)
      .processing(self.processing)
      .success(function() {
        repository.load(self.application().id);
      })
      .error(errorFn)
      .call();
  };

  self.submitInvitation = function() {
    self.error(undefined);
    var params = {"id":           self.application().id,
                  "taskId":       self.taskId() ? self.taskId() : "",
                  "foremanRole":  self.selectedRole() ? self.selectedRole() : "",
                  "foremanEmail": self.email() ? self.email() : "" };
    ajax.command("create-foreman-application", params)
      .processing(self.processing)
      .pending(self.pending)
      .success(function(data) {
        self.finished(data.id);
      })
      .error(errorFn)
      .call();

    return false;
  };

  self.submitTermination = function() {
    self.error(undefined);
    var foreman = self.selectedForTermination();
    var params = {"id":             self.application().id,
                  "foreman-app-id": foreman.id,
                  "foreman-email":  foreman.email,
                  "reason":         self.terminationReason()};
    var reloadFn = function() {
      repository.load(self.application().id);
      $(".dialog-close").trigger("click");
    };

    if (self.userIsAuthority()) {
      ajax.command("terminate-foreman", params)
        .processing(self.processing)
        .success(reloadFn)
        .error(errorFn)
        .call();
    } else {
      ajax.command("request-foreman-termination", params)
        .processing(self.processing)
        .success(reloadFn)
        .error(errorFn)
        .call();
    }
    return false;
  };

  self.removeListing = function() {
    self.error(undefined);
    var foreman = this;
    var params = {"id":             self.application().id,
                  "foreman-app-id": foreman.id};
    ajax.command("remove-foreman-from-list", params)
      .processing(self.processing)
      .success(function() {
        repository.load(self.application().id);
      })
      .error(errorFn)
      .call();
  };

  hub.subscribe({eventType: "dialog-close", id: "dialog-invite-foreman"}, function() {
    if (self.application() && self.finished()) {
      repository.load(self.application().id);
    }
  });

};
