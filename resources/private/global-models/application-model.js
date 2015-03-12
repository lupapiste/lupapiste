LUPAPISTE.ApplicationModel = function() {
  "use strict";
  var self = this;

  // POJSO
  self._js = {};

  // Observables
  self.id = ko.observable();

  self.auth = ko.observable();
  self.infoRequest = ko.observable();
  self.openInfoRequest = ko.observable();
  self.state = ko.observable();
  self.inPostVerdictState = ko.observable(false);
  self.summaryAvailable = ko.computed(function() {
    return self.inPostVerdictState() || self.state() === "canceled";
  });
  self.submitted = ko.observable();
  self.location = ko.observable();
  self.municipality = ko.observable();
  self.organizationMeta = ko.observable();
  self.organizationLinks = ko.computed(function() {
    return self.organizationMeta() ? self.organizationMeta().links() : "";
  });
  self.organizationName = ko.computed(function() {
    return self.organizationMeta() ? self.organizationMeta().name() : "";
  });
  self.requiredFieldsFillingObligatory = ko.computed(function() {
    return self.organizationMeta() ? self.organizationMeta().requiredFieldsFillingObligatory() : false;
  });
  self.incorrectlyFilledRequiredFields = ko.observable([]);
  self.hasIncorrectlyFilledRequiredFields = ko.computed(function() {
    return self.incorrectlyFilledRequiredFields() && self.incorrectlyFilledRequiredFields().length > 0;
  });
  self.permitType = ko.observable("R");
  self.propertyId = ko.observable();
  self.title = ko.observable();
  self.created = ko.observable();
  self.started = ko.observable();
  self.startedBy = ko.observable();
  self.closed = ko.observable();
  self.closedBy = ko.observable();
  self.attachments = ko.observable([]);
  self.hasAttachment = ko.computed(function() {
    return _.some((ko.toJS(self.attachments) || []), function(a) {return a.versions && a.versions.length;});
  });
  self.address = ko.observable();
  self.operations = ko.observable();
  self.permitSubtype = ko.observable();
  self.operationsCount = ko.observable();
  self.applicant = ko.observable();
  self.applicantPhone = ko.observable();
  self.assignee = ko.observable();
  self.neighbors = ko.observable([]);
  self.statements = ko.observable([]);
  self.tasks = ko.observable([]);
  self.taskGroups = ko.computed(function() {
    var tasks = ko.toJS(self.tasks) || [];
    // TODO query without foreman tasks
    tasks = _.filter(tasks, function(task) {
      return task["schema-info"].name !== "task-vaadittu-tyonjohtaja";
    });
    var schemaInfos = _.reduce(tasks, function(m, task){
      var info = task.schema.info;
      m[info.name] = info;
      return m;
    },{});

    var groups = _.groupBy(tasks, function(task) {return task.schema.info.name;});
    return _(groups)
      .keys()
      .map(function(n) {
        return {
          type: n,
          name: loc([n, "_group_label"]),
          order: schemaInfos[n].order,
          tasks: _.map(groups[n], function(task) {
            task.displayName = taskUtil.shortDisplayName(task);
            task.openTask = function() {
              taskPageController.setApplicationModelAndTaskId(self._js, task.id);
              window.location.hash = "!/task/" + self.id() + "/" + task.id;
            };
            task.statusName = LUPAPISTE.statuses[task.state] || "unknown";

            return task;
          })};})
      .sortBy("order")
      .valueOf();
  });

  self.foremanTasks = ko.observable();
  self.submittable = ko.observable(true);

  self.buildings = ko.observable([]);
  self.nonpartyDocumentIndicator = ko.observable(0);
  self.partyDocumentIndicator = ko.observable(0);
  self.linkPermitData = ko.observable(null);
  self.appsLinkingToUs = ko.observable(null);
  self.pending = ko.observable(false);
  self.processing = ko.observable(false);

  self.attachmentsRequiringAction = ko.observable();
  self.unseenStatements = ko.observable();
  self.unseenVerdicts = ko.observable();
  self.unseenComments = ko.observable();
  self.invites = ko.observableArray([]);
  self.showApplicationInfoHelp = ko.observable(false);
  self.showPartiesInfoHelp = ko.observable(false);
  self.showStatementsInfoHelp = ko.observable(false);
  self.showNeighborsInfoHelp = ko.observable(false);
  self.showVerdictInfoHelp = ko.observable(false);
  self.showSummaryInfoHelp = ko.observable(false);
  self.showConstructionInfoHelp = ko.observable(false);

  self.updateInvites = function() {
    invites.getInvites(function(data) {
      self.invites(_.filter(data.invites, function(invite) {
        return invite.application === self.id();
      }));
    });
  };

  // update invites when id changes
  self.id.subscribe(self.updateInvites);

  self.hasInvites = ko.computed(function() {
    return self.invites().length !== 0;
  });

  self.approveInvite = function() {
    ajax
      .command("approve-invite", {id: self.id()})
      .success(function() {
        self.reload();
        self.updateInvites();
      })
      .call();
    return false;
  };

  var acceptDecline = function(applicationId) {
    return function() {
      ajax
      .command("decline-invitation", {id: applicationId})
      .success(function() {window.location.hash = "!/applications";})
      .call();
      return false;
    };
  };

  self.declineInvite = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("applications.declineInvitation.header"),
      loc("applications.declineInvitation.message"),
      {title: loc("yes"), fn: acceptDecline(self.id())},
      {title: loc("no")}
    );
  };

  // Required attachments

  self.missingRequiredAttachments = ko.observable([]);
  self.hasMissingRequiredAttachments = ko.computed(function() {
    return self.missingRequiredAttachments() && self.missingRequiredAttachments().length > 0;
  });

  self.missingRequiredInfo = ko.computed(function() {
    return self.hasIncorrectlyFilledRequiredFields() || self.hasMissingRequiredAttachments();
  });

  self.submitButtonEnabled = ko.computed(function() {
    return !self.processing() && !self.hasInvites() && (!self.requiredFieldsFillingObligatory() || !self.missingRequiredInfo()) && self.submittable();
  });


  self.reload = function() {
    repository.load(self.id());
  };

  self.roles = ko.computed(function() {
    var withRoles = function(r, i) {
      var a = r[i.id()] || (i.roles = [], i);
      a.roles.push(i.role());
      r[i.id()] = a;
      return r;
    };
    var pimped = _.reduce(self.auth(), withRoles, {});
    return _.values(pimped);
  });

  self.openOskariMap = function() {
    var coords = "&coord=" + self.location().x() + "_" + self.location().y();
    var zoom = "&zoomLevel=12";
    var features = "&addPoint=1&addArea=1";
    var lang = "&lang=" + loc.getCurrentLanguage();
    var municipality = "&municipality=" + self.municipality();
    var url = "/oskari/fullmap.html?build=" + LUPAPISTE.config.build + "&id=" + self.id() + coords + zoom + features + lang + municipality;
    window.open(url);
  };

  self.submitApplication = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.submit.areyousure.title"),
      loc("application.submit.areyousure.message"),
      {title: loc("yes"),
       fn: function() {
        ajax.command("submit-application", {id: self.id()})
          .success(function() {
            self.reload();
          })
          .processing(self.processing)
          .call();
        return false;
      }},
      {title: loc("no")}
    );
    return false;
  };

  self.requestForComplement = function(model) {
    ajax.command("request-for-complement", { id: self.id()})
      .success(function() {
        notify.success("pyynt\u00F6 l\u00E4hetetty",model);
        self.reload();
      })
      .processing(self.processing)
      .call();
    return false;
  };

  self.convertToApplication = function() {
    ajax.command("convert-to-application", {id: self.id()})
      .success(function() {
        window.location.hash = "!/application/" + self.id();
      })
      .processing(self.processing)
      .call();
    return false;
  };

  self.approveApplication = function() {
    ajax.command("approve-application", {id: self.id(), lang: loc.getCurrentLanguage()})
      .success(function(resp) {
        self.reload();
        if (!resp.integrationAvailable) {
          LUPAPISTE.ModalDialog.showDynamicOk(loc("integration.title"), loc("integration.unavailable"));
        }
      })
      .error(function(e) {LUPAPISTE.showIntegrationError("integration.title", e.text, e.details);})
      .processing(self.processing)
      .call();
    return false;
  };

  self.refreshKTJ = function(model) {
    ajax.command("refresh-ktj", {id: self.id()})
      .success(function() {
        self.reload();
        //FIXME parempi tapa ilmoittaa onnistumisesta
        notify.success("KTJ tiedot p\u00e4ivitetty",model);
      })//FIXME parempi/tyylikaampi virheilmoitus
      .error(function(resp) {alert(resp.text);})
      .processing(self.processing)
      .call();
    return false;
  };

  self.removeAuth = function(model) {
    var username = model.username();
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("areyousure.message"),
      {title: loc("yes"),
       fn:  function() {
         ajax.command("remove-auth", { id: self.id(), username: username})
           .success(self.reload)
           .processing(self.processing)
           .call();
         return false;
      }},
      {title: loc("no")}
    );
    return false;
  };

  self.isNotOwner = function(model) {
    return model.role() !== "owner";
  };

  self.canSubscribe = function(model) {
    return model.role() !== "statementGiver" && currentUser && (currentUser.isAuthority() || currentUser.id() ===  model.id());
  };

  self.manageSubscription = function(command, model) {
    if (self.canSubscribe(model)) {
      ajax.command(command, {id: self.id(), username: model.username()})
        .success(self.reload)
        .processing(self.processing)
        .pending(self.pending)
        .call();
    }
  };

  self.subscribeNotifications = _.partial(self.manageSubscription, "subscribe-notifications");
  self.unsubscribeNotifications = _.partial(self.manageSubscription, "unsubscribe-notifications");

  self.addOperation = function() {
    window.location.hash = "!/add-operation/" + self.id();
    return false;
  };

  self.cancelInforequest = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("areyousure.cancel-inforequest"),
      {title: loc("yes"),
       fn: function() {
        ajax
          .command("cancel-inforequest", {id: self.id()})
          .success(function() {window.location.hash = "!/applications";})
          .processing(self.processing)
          .call();
        return false;}},
      {title: loc("no")}
    );
    return false;
  };

  self.cancelApplication = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("areyousure.cancel-application"),
      {title: loc("yes"),
       fn: function() {
        ajax
          .command("cancel-application", {id: self.id()})
          .success(function() {window.location.hash = "!/applications";})
          .processing(self.processing)
          .call();
        return false;}},
      {title: loc("no")}
    );
    return false;
  };

  self.cancelText = ko.observable("");

  self.cancelApplicationAuthority = function() {
    LUPAPISTE.ModalDialog.setDialogContent(
      $("#dialog-cancel-application"),
      loc("areyousure"),
      loc("areyousure.cancel-application"),
      {title: loc("yes"),
       fn: function() {
        ajax
          .command("cancel-application-authority", {id: self.id(), text: self.cancelText()})
          .success(function() {
            self.cancelText("");
            window.location.hash = "!/applications";
          })
          .processing(self.processing)
          .call();
        return false;}},
      {title: loc("no")}
    );
    LUPAPISTE.ModalDialog.open("#dialog-cancel-application");
  };

  self.exportPdf = function() {
    window.open("/api/raw/pdf-export?id=" + self.id() + "&lang=" + loc.currentLanguage, "_blank");
    return false;
  };


  self.newOtherAttachment = function() {
    attachment.initFileUpload({
      applicationId: self.id(),
      attachmentId: null,
      attachmentType: "muut.muu",
      typeSelector: false
    });
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };

  self.createChangePermit = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.createChangePermit.areyousure.title"),
      loc("application.createChangePermit.areyousure.message"),
      {title: loc("application.createChangePermit.areyousure.ok"),
       fn: function() {
        ajax
          .command("create-change-permit", {id: self.id()})
          .success(function(data) {
            window.location.hash = "!/application/" + data.id;
          })
          .processing(self.processing)
          .call();
        return false;
      }},
      {title: loc("cancel")}
    );
    return false;

  };


  self.doCreateContinuationPeriodPermit = function() {
    ajax
      .command("create-continuation-period-permit", {id: self.id()})
      .success(function(data) {
        window.location.hash = "!/application/" + data.id;
      })
      .processing(self.processing)
      .call();
  };

  self.createContinuationPeriodPermit = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("application.createContinuationPeriodPermit.confirmation.title"),
        loc("application.createContinuationPeriodPermit.confirmation.message"),
        {title: loc("yes"), fn: self.doCreateContinuationPeriodPermit},
        {title: loc("no")}
    );
  };

  self.resetIndicators = function() {
    ajax
      .command("mark-everything-seen", {id: self.id()})
      .success(self.reload)
      .processing(self.processing)
      .call();
  };

  self.goToTabPosition = function(targetTab, targetId) {
    if (targetTab === "requiredFieldSummary") {
      ajax
        .command("fetch-validation-errors", {id: self.id()})
        .success(function (data) {
          self.updateMissingApplicationInfo(data.results);
        })
        .processing(self.processing)
        .call();
    }
    window.location.hash = "!/application/" + self.id() + "/" + targetTab;
    if (targetId) {
      // The Nayta-links in "Puuttuvat pakolliset tiedot"-list do not work properly without using
      // the setTimeout function with 0 time here.
      setTimeout(function() {
        window.scrollTo(0, $("#" + targetId).offset().top - 60);
      }, 0);
    }
  };

  self.changeTab = function(model,event) {
    var targetTab = $(event.target).closest("a").attr("data-target");
    self.goToTabPosition(targetTab, null);
  };

  self.nextTab = function(model,event) {
    var targetTab = $(event.target).closest("a").attr("data-target");
    self.goToTabPosition(targetTab, "applicationTabs");
  };

  self.moveToIncorrectlyFilledRequiredField = function(fieldInfo) {
    var targetTab = (fieldInfo.document.type !== "party") ? "info" : "parties";
    var targetId = fieldInfo.document.id + "-" + fieldInfo.path.join("-");
    self.goToTabPosition(targetTab, targetId);
  };

  self.moveToMissingRequiredAttachment = function(fieldInfo) {
    var targetTab = "attachments";
    var targetId = "attachment-row-" + fieldInfo.type["type-group"]() + "-" + fieldInfo.type["type-id"]();
    self.goToTabPosition(targetTab, targetId);
  };

  function extractMissingAttachments(attachments) {
    var missingAttachments = _.filter(attachments, function(a) {
      var required = a.required ? a.required() : false;
      var notNeeded = a.notNeeded ? a.notNeeded() : false;
      var versionsExist = a.versions() && a.versions().length;
      return required && !notNeeded && !versionsExist;
    });
    missingAttachments = _.groupBy(missingAttachments, function(a){ return a.type["type-group"](); });
    missingAttachments = _.map(_.keys(missingAttachments), function(k) {
      return [k, missingAttachments[k]];
    });
    return missingAttachments;
  }

  self.updateMissingApplicationInfo = function(errors) {
    self.incorrectlyFilledRequiredFields(util.extractRequiredErrors(errors));
    self.missingRequiredAttachments(extractMissingAttachments(self.attachments()));
  };

  self.toggleHelp = function(param) {
    self[param](!self[param]());
  };

  self.toAsianhallinta = function() {
    ajax.command("application-to-asianhallinta", {id: self.id(), lang: loc.getCurrentLanguage()})
      .success(function() {
        self.reload();
      })
      .error(function(e) {LUPAPISTE.showIntegrationError("integration.asianhallinta.title", e.text, e.details);})
      .processing(self.processing)
      .call();
  };
};
