LUPAPISTE.EmptyApplicationModel = function() {
  "use strict";
  return {startedBy: {firstName: "", lastName: ""},
          closedBy: {firstName: "", lastName: ""},
          warrantyStart: null,
          warrantyEnd: null,
          expiryDate: null};
};

LUPAPISTE.ApplicationModel = function() {
  "use strict";
  var self = this;

  // POJSO
  self._js = {};

  function fullNameInit() {
    return {firstName: ko.observable( "" ),
           lastName: ko.observable( "")};
  }

  // Observables
  self.id = ko.observable();

  self.auth = ko.observable();
  self.infoRequest = ko.observable();
  self.openInfoRequest = ko.observable();
  self.state = ko.observable();
  self.stateChanged = ko.observable(false);
  self.submitted = ko.observable();
  self.location = ko.observable();
  self["location-wgs84"] = ko.observable();
  self.municipality = ko.observable();
  self.permitType = ko.observable();
  self.propertyId = ko.observable();
  self.propertyIdSource = ko.observable();
  self.title = ko.observable();
  self.created = ko.observable();
  self.modified = ko.observable();
  self.started = ko.observable();
  self.startedBy = fullNameInit();
  self.closed = ko.observable();
  self.closedBy = fullNameInit();
  self.warrantyStart = ko.observable();
  self.warrantyEnd = ko.observable();
  self.address = ko.observable();
  self.secondaryOperations = ko.observable();
  self.primaryOperation = ko.observable();
  self.allOperations = ko.observable();
  self.reviewOfficers = ko.observable();

  self.permitSubtype = ko.observable();
  self.permitSubtypeHelp = ko.pureComputed(function() {
    var opName = util.getIn(self, ["primaryOperation", "name"]);
    if (loc.hasTerm(["help", opName ,"subtype"])) {
      return "help." + opName + ".subtype";
    }
    return undefined;
  });
  self.permitSubtypes = ko.observableArray([]);
  self.permitSubtypeMandatory = ko.pureComputed(function() {
    return !self.permitSubtype() && !_.isEmpty(self.permitSubtypes());
  });

  self.titleForPartiesOrSupervisor = ko.pureComputed(function() {
    return util.getIn(self, ["primaryOperation", "name"]) === "tyonjohtajan-nimeaminen-v2"
      ? "application.tabSupervisorInformation"
      : "application.tabParties";
  });

  self.descForPartiesOrSupervisor = ko.pureComputed(function() {
    return util.getIn(self, ["primaryOperation", "name"]) === "tyonjohtajan-nimeaminen-v2"
      ? "help.supervisorInformationDesc"
      : "help." + self.permitType() + ".PartiesDesc";
  });

  self.operationsCount = ko.observable();
  self.applicant = ko.observable();
  self.creator = ko.observable();
  self.assignee = ko.observable();
  self.authority = ko.observable({});
  self.neighbors = ko.observable([]);
  self.statements = ko.observable([]);
  self.tasks = ko.observable([]);
  self.tosFunction = ko.observable(null);
  self.metadata = ko.observable();
  self.processMetadata = ko.observable();
  self.kuntalupatunnukset = ko.observable();
  self["pate-verdicts"] = ko.observable([]);
  self.continuationPeriods = ko.observable([]);
  self.expiryDate = ko.observable();
  self.showExpiryDate = ko.observable();
  self.showContinuationDate = ko.observable();

  // Options
  self.optionMunicipalityHearsNeighbors = ko.observable(false);
  self.optionMunicipalityHearsNeighborsDisabled = ko.pureComputed(function() {
    return !lupapisteApp.models.applicationAuthModel.ok("set-municipality-hears-neighbors");
  });
  self.municipalityHearsNeighborsVisible = ko.pureComputed( function() {
    return lupapisteApp.models.applicationAuthModel.ok( "municipality-hears-neighbors-visible");
  });

  // Use writable computed to init value by auth model without triggering save command
  var submitRestrictionForOtherAuths = ko.observable();
  self.optionSubmitRestrictionForOtherAuths = ko.pureComputed({
    read: function() {
      // Default value is determined by the auth model
      return _.isNil(submitRestrictionForOtherAuths()) ?
        lupapisteApp.models.applicationAuthModel.ok("submit-restriction-enabled-for-other-auths") :
        submitRestrictionForOtherAuths();
    },
    write: function(enabled) {
      // If value is changed manually it is stored in the observable and save command is triggered
      submitRestrictionForOtherAuths(enabled);
      ajax.command("toggle-submit-restriction-for-other-auths", {id: self.id(),
                                                                 "apply-submit-restriction": enabled})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .processing(self.processing)
        .call();
    }
  });
  self.optionSubmitRestrictionForOtherAuthsDisabled = ko.pureComputed(function() {
    return !lupapisteApp.models.applicationAuthModel.ok("toggle-submit-restriction-for-other-auths");
  });
  self.submitRestrictionForOtherAuthsVisible = ko.pureComputed(function() {
    return lupapisteApp.models.applicationAuthModel.ok("authorized-to-apply-submit-restriction-to-other-auths");
  });

  // Application indicator metadata fields
  self.unseenStatements = ko.observable();
  self.unseenVerdicts = ko.observable();
  self.unseenComments = ko.observable();
  self.unseenAuthorityNotice = ko.observable();
  self.attachmentsRequiringAction = ko.observable();
  self.fullyArchived = ko.observable();

  // Application metadata fields
  self.inPostVerdictState = ko.observable(false);
  self.tasksTabShouldShow = ko.observable(false);
  self.stateSeq = ko.observable([]);
  self.currentStateInSeq = ko.pureComputed(function() {return _.includes(self.stateSeq(), self.state());});
  self.inPostSubmittedState = ko.observable(false); // TODO: remove
  self.vendorBackendId = ko.observable(); // TODO: remove
  self.applicantPhone = ko.observable();
  self.organizationMeta = ko.observable();
  self.neighbors = ko.observable([]);
  self.submitErrors = ko.observableArray();
  self.applicantCompanies = ko.observableArray();

  self.organization = ko.observable([]);

  self.organizationName = ko.pureComputed(function() {
    return self.organizationMeta() ? self.organizationMeta().name() : "";
  });
  self.requiredFieldsFillingObligatory = ko.pureComputed(function() {
    return self.organizationMeta() ? self.organizationMeta().requiredFieldsFillingObligatory() : false;
  });
  self.incorrectlyFilledRequiredFields = ko.observable([]);
  self.hasIncorrectlyFilledRequiredFields = ko.pureComputed(function() {
    return self.incorrectlyFilledRequiredFields() && self.incorrectlyFilledRequiredFields().length > 0;
  });
  self.fieldWarnings = ko.observable([]);
  self.hasFieldWarnings = ko.computed(function() {
    return self.fieldWarnings() && self.fieldWarnings().length > 0;
  });

  self.urgency = ko.observable();
  self.authorityNotice = ko.observable();
  self.tags = ko.observableArray([]);
  self.comments = ko.observable([]);

  self.summaryAvailable = ko.pureComputed(function() {
    return lupapisteApp.models.applicationAuthModel.ok("application-summary-tab-visible");
  });

  self.isArchivingProject = ko.pureComputed(function() {
    return self.permitType() === "ARK";
  });

  self.isEnvironmentApplication = ko.pureComputed(function() {
    return _.some(["YI", "YM", "YL", "VVVL", "MAL"],
        function (ympPermiType) { return self.permitType() === ympPermiType; });
  });

  self.tabInfoLocKey = ko.pureComputed(function() {
    if (self.isArchivingProject()) {
      return "application.archivingProject.tabInfo";
    }
    if (self.isEnvironmentApplication()) {
      return "application.environmentApplication.tabInfo";
    }
    return "application.tabInfo";
  });

  self.openTask = function( taskId ) {
    hub.send( "scrollService::push");
    taskPageController.setApplicationModelAndTaskId(self._js, taskId);
    pageutil.openPage("task",  self.id() + "/" + taskId);
  };

  self.taskGroups = ko.pureComputed(function() {
    var allTasks = ko.toJS(self.tasks) || [];
    var order = lupapisteApp.services.taskService.planOrder();
    var tasks = _(_.map( order, function( id ) {
      return _.find( allTasks, {id: id});
    }))
        .filter()
        .value();

    var groups = _.groupBy(tasks, function(task) {return task.schema.info.name;});
    return _(groups)
      .keys()
      .map(function(n) {
        return {
          type: n,
          name: loc([n, "_group_label"]),
          tasks: _.map(groups[n], function(task) {
            task.displayName = taskUtil.shortDisplayName(task);
            task.openTask = _.partial( self.openTask, task.id);
            task.statusName = LUPAPISTE.statuses[task.state] || "unknown";

            return task;
          })};})
      .sortBy("order")
      .valueOf();
  });

  self.primaryOperationName = ko.pureComputed(function() {
    var opName = util.getIn(self.primaryOperation, ["name"]);
    return !_.isEmpty(opName) ? "operations." + opName : "";
  });

  hub.subscribe("op-description-changed", function(e) {
    var opid = e["op-id"];
    var desc = e["op-desc"];

    if (e.appId === self.id()) {
      var operations = _.map(self.allOperations(), function(op) {
        return op.id() === opid ? op.description(desc) : op;
      });
      self.allOperations(operations);
    }
  });

  self.foremanTasks = ko.observable();

  self.nonpartyDocumentIndicator = ko.observable(0);
  self.partyDocumentIndicator = ko.observable(0);

  self.calendarNotificationIndicator = ko.observable(0);
  self.calendarNotificationsPending = ko.observableArray([]);

  self.bulletinOpDescription = ko.observable().extend({rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});
  self.opDescriptionIndicator = ko.observable().extend({notify: "always"});

  self.linkPermitData = ko.observable(null);
  self.appsLinkingToUs = ko.observable(null);
  self.pending = ko.observable(false);
  self.processing = ko.observable(false);
  self.invites = ko.observableArray([]);
//  self.showApplicationInfoHelp = ko.observable(false);
//  self.showPartiesInfoHelp = ko.observable(false);
  // self.showStatementsInfoHelp = ko.observable(false);
  // self.showNeighborsInfoHelp = ko.observable(false);
  // self.showVerdictInfoHelp = ko.observable(false);
  self.showSummaryInfoHelp = ko.observable(false);
  self.showConstructionInfoHelp = ko.observable(false);

  self.targetTab = ko.observable({tab: undefined, id: undefined});

  self.allowedAttachmentTypes = ko.observableArray([]);

  self.handlingTimeText = ko.pureComputed( function() {
    var timeLeft = lupapisteApp.services.organizationsHandlingTimesService.getTimeLeft(self.organization(), self.submitted());
    if (self.state() === "submitted" && _.isNumber(timeLeft)) {
      var absTimeLeft = Math.abs(timeLeft);
      var ltext = timeLeft < 0 ? "application.handling-time.negative" : "application.handling-time.positive";
      return loc(absTimeLeft === 1 ? ltext + ".one" : ltext, absTimeLeft);
    }
  });

  self.toBackingSystem = function() {
    window.open("/api/raw/redirect-to-vendor-backend?id=" + self.id(), "backend");
  };

  self.updateInvites = function() {
    invites.getInvites(function(data) {
      self.invites(_.filter(data.invites, function(invite) {
        return invite.application.id === self.id();
      }));
      if (self.hasPersonalInvites()) {
        self.showAcceptPersonalInvitationDialog();
      }
      if (self.hasCompanyInvites()) {
        self.showAcceptCompanyInvitationDialog();
      }
    });
  };

  // update invites when id changes
  self.id.subscribe(self.updateInvites);

  self.hasInvites = ko.computed(function() {
    return !_.isEmpty(self.invites());
  });

  self.hasPersonalInvites = ko.computed(function() {
    var id = util.getIn(lupapisteApp.models.currentUser, ["id"]);
    return !_.isEmpty(_.filter(self.invites(), ["user.id", id]));
  });

  self.hasCompanyInvites = ko.computed(function() {
    var id = util.getIn(lupapisteApp.models.currentUser, ["company", "id"]);
    return !_.isEmpty(_.filter(self.invites(), ["user.id", id]));
  });

  function approveInvite(type, opts) {
    var unwrappedOpts = {"apply-submit-restriction": util.getIn(opts, ["applySubmitRestriction"])};
    ajax
      .command("approve-invite", _.assign({id: self.id(), "invite-type": type}, unwrappedOpts))
      .success(function() {
        self.reload();
        self.updateInvites();
      })
      .call();
    return false;
  }

  self.approveInvite = function(type) {
    if (type === "company" && lupapisteApp.models.globalAuthModel.ok("authorized-to-apply-submit-restriction-to-other-auths")) {
      self.showAcceptCompanyInvitationDialog();
    } else {
      approveInvite(type);
    }
  };

  var acceptDecline = function(applicationId) {
    return function() {
      ajax
      .command("decline-invitation", {id: applicationId})
      .success(function() {pageutil.openPage("applications");})
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

  self.missingRequiredAttachments = ko.pureComputed( function() {
    return _.get( lupapisteApp, "services.attachmentsService.missingRequiredAttachments", _.noop)();
  });

  self.hasMissingRequiredAttachments = ko.pureComputed(function() {
    return !_.isEmpty( self.missingRequiredAttachments());
  });

  self.missingSomeInfo = ko.pureComputed(function() {
    return self.hasFieldWarnings() || self.hasIncorrectlyFilledRequiredFields() || self.hasMissingRequiredAttachments();
  });

  self.submitButtonEnabled = ko.pureComputed(function() {
    return !self.stateChanged()
      && !self.processing()
      && !self.hasInvites()
      && (!self.requiredFieldsFillingObligatory()
          || !self.missingSomeInfo())
      && _.isEmpty(self.submitErrors())
      && (lupapisteApp.models.applicationAuthModel.ok( "submit-application") ||
          lupapisteApp.models.applicationAuthModel.ok( "submit-archiving-project"));
  });

  self.submitButtonFunction = ko.pureComputed(function() {
    if (lupapisteApp.models.applicationAuthModel.ok("submit-archiving-project")) {
      return self.submitArchivingProject;
    }
    else if (lupapisteApp.models.applicationAuthModel.ok("application-submittable")) {
      return self.submitApplication;
    } else {
      return false;
    }
  });

  self.submitButtonKey = ko.pureComputed(function() {
    if (self.isArchivingProject()) {
      return lupapisteApp.models.currentUser.isArchivist() ? "digitizer.archiveProject" : "digitizer.submitProject";
    } else {
      return "application.submitApplication";
    }
  });

  self.reload = function() {
    self.submitErrors([]);
    repository.load(self.id());
  };

  self.reloadToTab = function(tabName) {
    self.submitErrors([]);
    repository.load(self.id(), undefined, _.partial(self.open, tabName));
  };

  self.lightReload = function() {
    repository.load(self.id(), undefined, undefined, true);
  };

  function withRoles(r, i) {
      if (i.id() === "" && i.invite) {
        i.id(util.getIn(i, ["invite", "user", "id"]));
      }
      var auth = r[i.id()] || (i.roles = [], i);
      var role = i.role();
      if (!_.includes(auth.roles, role)) {
        auth.roles.push(role);
      }
      r[i.id()] = auth;
      return r;
  }

  self.roles = ko.pureComputed(function() {
    var pimped = _.reduce(self.auth(), withRoles, {});
    return _.values(pimped);
  });

  self.submitApplication = function() {
    if (!self.stateChanged()) {
      hub.send("show-dialog", {
        ltitle: "application.submit.areyousure.title",
        size: "medium",
        component: "yes-no-dialog",
        componentParams: {
          ltext: "application.submit.areyousure.message",
          yesFn: function() {
            ajax.command("submit-application", {id: self.id()})
              .success( self.reload)
              .onError("error.cannot-submit-application", cannotSubmitResponse)
              .onError("error.command-illegal-state", self.lightReload)
              .fuse(self.stateChanged)
              .processing(self.processing)
              .call();
            return false;
          }
        }
      });
    }
    return false;
  };

  self.submitArchivingProject = function() {
    if (!self.stateChanged()) {
      ajax.command("submit-archiving-project", {id: self.id()})
        .success(function(){
          if (lupapisteApp.models.currentUser.isArchivist()) {
            self.reloadToTab("archival");
          } else {
            self.reload();
          }
        })
        .onError("error.cannot-submit-application", cannotSubmitResponse)
        .onError("error.command-illegal-state", self.lightReload)
        .fuse(self.stateChanged)
        .processing(self.processing)
        .call();
    }
    return false;
  };

  self.requestForComplement = function() {
    ajax.command("request-for-complement", { id: self.id()})
      .success(function() {
        ajax.command( "cleanup-krysp", {id: self.id()})
          .onError(_.noop)
          .call();
        self.reload();
      })
      .onError("error.command-illegal-state", self.lightReload)
      .fuse(self.stateChanged)
      .processing(self.processing)
      .call();
    return false;
  };

  self.convertToApplication = function() {
    ajax.command("convert-to-application", {id: self.id()})
      .success(function() {
        pageutil.openPage("application", self.id());
      })
      .fuse(self.stateChanged)
      .processing(self.processing)
      .call();
    return false;
  };

  self.nonApprovedDesigners = ko.observableArray([]);

  function checkForNonApprovedDesigners() {
    if (lupapisteApp.models.applicationAuthModel.ok("approve-doc")) {
      var nonApproved = _(docgen.nonApprovedDocuments()).filter(function(docModel) {
        return docModel.schema.info.subtype === "suunnittelija" && !docModel.docDisabled;
      })
      .filter(function(designerDoc) {
        return !self.inPostVerdictState() ? true : designerDoc.schema.info["post-verdict-party"];
      })
      .map(function(docModel) {
        var title = loc([docModel.schemaName, "_group_label"]);
        var accordionService = lupapisteApp.services.accordionService;
        var identifierField = accordionService.getIdentifier(docModel.docId);
        var identifier = identifierField && identifierField.value();
        var operation = null; // We'll assume designer is never attached to operation
        var docData = accordionService.getDocumentData(docModel.docId); // The current data
        var accordionText = docutils.accordionText(docData.accordionPaths, docData.data);
        var headerDescription = docutils.headerDescription(identifier, operation, accordionText);

        return title + headerDescription;
      })
      .value();
      self.nonApprovedDesigners(nonApproved);
    }
  }

  hub.subscribe("update-doc-success", checkForNonApprovedDesigners);
  hub.subscribe("application-model-updated", checkForNonApprovedDesigners);
  hub.subscribe({eventType:"approval-status", broadcast: true}, _.debounce(checkForNonApprovedDesigners));

  function showIntegrationError( response ) {
    util.showIntegrationError({ltext: response.text, details: response.details});
  }

  self.approveApplication = function() {
    if (self.stateChanged()) {
      return false;
    }

    var approve = function() {
      ajax.command("approve-application", {id: self.id(), lang: loc.getCurrentLanguage()})
        .success(function(resp) {
          self.reloadToTab("info");
          if (!resp.integrationAvailable) {
            hub.send("show-dialog", {ltitle: "integration.title",
                                     size: "medium",
                                     component: "ok-dialog",
                                     componentParams: {ltext: "integration.unavailable"}});
          } else if (self.externalApi.enabled()) {
            var permit = externalApiTools.toExternalPermit(self._js);
            hub.send("external-api::integration-sent", permit);
          }
        })
        .onError("error.command-illegal-state", self.lightReload)
        .error( showIntegrationError )
        .fuse(self.stateChanged)
        .processing(self.processing)
        .call();
    };

    if (!( lupapisteApp.models.applicationAuthModel.ok( "statements-after-approve-allowed")
           || _(self._js.statements).reject("given").isEmpty())) {
      // All statements have not been given
      hub.send("show-dialog", {ltitle: "application.approve.statement-not-requested",
        size: "medium",
        component: "yes-no-dialog",
        componentParams: {ltext: "application.approve.statement-not-requested-warning-text",
          yesFn: approve}});
    } else {
      approve();
    }
    return false;
  };

  self.approveApplicationAfterVerdict = function() {
    if (self.stateChanged()) {
      return false;
    }

    var approve = function() {
      ajax.command("approve-application-after-verdict", {id: self.id(), lang: loc.getCurrentLanguage()})
          .success(function(resp) {
            if (!resp.integrationAvailable) {
              hub.send("show-dialog", {ltitle: "integration.title",
                size: "medium",
                component: "ok-dialog",
                componentParams: {ltext: "integration.unavailable"}});
            } else {
              if (self.externalApi.enabled()) {
                var permit = externalApiTools.toExternalPermit(self._js);
                hub.send("external-api::integration-sent", permit);
              }
              //self.reloadToTab("info");
              self.lightReload();
              util.showSavedIndicator(resp);
            }
          })
          .onError("error.command-illegal-state", self.lightReload)
          .error( showIntegrationError )
          .processing(self.processing)
          .call();
    };

    approve();
    return false;
  };

  function toApproveApplicationNext() {
    if (lupapisteApp.models.applicationAuthModel.ok("pate-enabled-basic") &&
        (self.hasFieldWarnings()
         || self.hasIncorrectlyFilledRequiredFields()
         || self.hasMissingRequiredAttachments())) {
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("application.approve.missing-required-info.warning-title"),
        loc("application.approve.missing-required-info.warning-text"),
        {title: loc("application.approve.missing-info.continue-anyway"), fn: self.approveApplication},
        {title: loc("application.approve.missing-info.cancel")}
      );
    } else {
      self.approveApplication();
    }
  }

  self.toApproveApplication = function () {
    if( lupapisteApp.models.applicationAuthModel.ok( "change-permit-premises-note")) {
      hub.send( "show-dialog",
                {ltitle: "permitSubtype.muutoslupa",
                 size: "medium",
                 component: "yes-no-dialog",
                 componentParams: {ltext: "application.change-permit.premises.confirmation",
                                   lyesTitle: "application.approveApplication",
                                   lnoTitle: "cancel",
                                   yesFn: toApproveApplicationNext }});
    } else {
      toApproveApplicationNext();
    }
  };

  self.toApproveApplicationAfterVerdict = function () {
    self.approveApplicationAfterVerdict();
   //TODO ADD WARNINGS FOR USER
  };


  self.partiesAsKrysp = function() {
    var sendParties = function() {
      ajax.command("parties-as-krysp", {id: self.id(), lang: loc.getCurrentLanguage()})
        .success(function(resp) {
          hub.send("indicator", {style: "positive", rawMessage: loc("integration.parties.sent", resp.sentDocuments.length), sticky: true});
          self.lightReload();
        })
        .onError("error.command-illegal-state", self.lightReload)
        .error( showIntegrationError )
        .processing(self.processing)
        .call();
    };

    // All designers have not been approved?
    if (!_.isEmpty(self.nonApprovedDesigners())) {
      var text = loc("application.designers-not-approved-help") + "<ul><li>" + self.nonApprovedDesigners().join("</li><li>") + "</li></ul>";
      hub.send("show-dialog", {ltitle: "application.designers-not-approved",
      size: "medium",
      component: "yes-no-dialog",
      componentParams: {text: text, yesFn: sendParties, lyesTitle: "continue", lnoTitle: "cancel"}});
    } else {
      sendParties();
    }
  };

  self.approveExtension = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.extension.approve"),
      loc("application.extension.approve-confirmation"),
      {title: loc("ok"), fn: self.approveApplication},
      {title: loc("cancel")}
    );
  };

  self.refreshKTJ = function() {
    ajax.command("refresh-ktj", {id: self.id()})
      .success(function() {
        self.reload();
        LUPAPISTE.ModalDialog.showDynamicOk(loc("integration.title"), loc("application.refreshed"));
      })
      .processing(self.processing)
      .call();
    return false;
  };

  self.findOwners = function() {
    hub.send("show-dialog", { ltitle: "neighbor.owners.title",
                              size: "large",
                              minContentHeight: "20em",
                              component: "neighbors-owners-dialog",
                              componentParams: {applicationId: self.id()} });
    return false;
  };

  self.userHasRole = function(userModel, role) {
    return _(util.getIn(self.roles()))
      .filter(function(r) { return r.id() === util.getIn(userModel, ["id"]); })
      .invokeMap("role")
      .includes(role);
  };

  self.canSubscribe = function(model) {
    var user = lupapisteApp.models.currentUser;
    return model.role() !== "statementGiver"
      && user
      && (user.isAuthority()
          || user.id() ===  model.id()
          || (user.isCompanyUser()
              && user.company.id() === model.id()
              && user.company.role() === "admin"))
      && lupapisteApp.models.applicationAuthModel.ok("toggle-notification-subscription");
  };

  self.manageSubscription = function(subscribe, model) {
    if (self.canSubscribe(model)) {
      ajax.command("toggle-notification-subscription", {id: self.id(),
                                                        authId: model.id(),
                                                        subscribe: subscribe})
        .success(self.reload)
        .processing(self.processing)
        .pending(self.pending)
        .call();
    }
  };

  self.subscribeNotifications = _.partial(self.manageSubscription, true );
  self.unsubscribeNotifications = _.partial(self.manageSubscription, false);

  self.addOperation = function() {
    pageutil.openPage("add-operation", self.id());
    return false;
  };

  self.cancelInforequest = function() {
    if (!self.stateChanged()) {
      hub.send( "show-dialog", {
        ltitle: "areyousure",
        size: "medium",
        component: "yes-no-dialog",
        componentParams: {
          ltext: "areyousure.cancel-inforequest",
          lyesTitle: "yes",
          lnoTitle: "no",
          yesFn: function() {
            ajax
              .command("cancel-inforequest", {id: self.id()})
              .success(function() {pageutil.openPage("applications");})
              .onError("error.command-illegal-state", self.lightReload)
              .fuse(self.stateChanged)
              .processing(self.processing)
              .call();
            return false;
          }
        }
      });
    }
    return false;
  };

  self.cancelText = ko.observable("");

  function cancelApplicationAjax(command) {
    return function() {
      ajax
        .command(command, {id: self.id(), text: self.cancelText(), lang: loc.getCurrentLanguage()})
        .success(function() {
          self.cancelText("");
          if (command === "cancel-application") {
            // regular user, can't undo cancellation so redirect to applications view
            pageutil.openPage("applications");
          } else { // authority, can undo so don't redirect, just reload application to canceled state
            self.lightReload();
          }
        })
        .onError("error.command-illegal-state", self.lightReload)
        .fuse(self.stateChanged)
        .processing(self.processing)
        .call();
      return false;
    };
  }

  self.cancelApplication = function() {
    if (!self.stateChanged()) {
      hub.send("show-dialog", {ltitle: self.isArchivingProject() ? "application.cancelArchivingProject" : "application.cancelApplication",
                               size: "medium",
                               component: self.isArchivingProject() ? "yes-no-dialog" : "textarea-dialog",
                               componentParams: {text: self.isArchivingProject() ? loc("areyousure.cancelArchivingProject") : loc("areyousure.cancel-application"),
                                                 yesFn: cancelApplicationAjax("cancel-application"),
                                                 lyesTitle: "yes",
                                                 lnoTitle: "no",
                                                 textarea: {llabel: "application.canceled.reason",
                                                            rows: 10,
                                                            observable: self.cancelText}}});
    }
  };

  self.undoCancellation = function() {
    if (!self.stateChanged()) {
      var sendCommand = ajax
                          .command("undo-cancellation", {id: self.id()})
                          .success(function() {
                            repository.load(self.id());
                          })
                          .onError("error.command-illegal-state", self.lightReload)
                          .fuse(self.stateChanged)
                          .processing(self.processing);

      hub.send("show-dialog", {ltitle: "application.undoCancellation",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {text: loc("application.undoCancellation.areyousure", loc(util.getPreviousState(self._js))),
                                                 yesFn: function() { sendCommand.call(); }}});
    }
  };

  self.exportPdf = function() {
    window.open("/api/raw/pdf-export?id=" + self.id() + "&lang=" + loc.currentLanguage, "_blank");
    return false;
  };


  self.createChangePermit = function() {
    hub.send( "show-dialog", {
      ltitle: "application.createChangePermit.areyousure.title",
      size: "medium",
      component: "yes-no-dialog",
      componentParams: {
        ltext: "application.createChangePermit.areyousure.message",
        lyesTitle: "application.createChangePermit.areyousure.ok",
        yesFn: function() {
          ajax
            .command("create-change-permit", {id: self.id()})
            .success(function(data) {
              pageutil.openPage("application", data.id);
            })
            .processing(self.processing)
            .call();
          return false;
        },
        lnoTitle: "cancel"
      }
    });
  };

  self.doCreateContinuationPeriodPermit = function() {
    ajax
      .command("create-continuation-period-permit", {id: self.id()})
      .success(function(data) {
        pageutil.openPage("application", data.id);
      })
      .processing(self.processing)
      .call();
  };

  self.createContinuationPeriodPermit = function() {
    var primaryOpName = lupapisteApp.models.application.primaryOperation().name();
    var genericMessage = "application.createContinuationPeriodPermit.confirmation.message";
    var opSpecificMessage = "application.createContinuationPeriodPermit.confirmation.message." + primaryOpName;
    var hasOpMessage = loc.terms[opSpecificMessage] !== undefined;

    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("application.createContinuationPeriodPermit.confirmation.title"),
        loc(hasOpMessage ? opSpecificMessage : genericMessage),
        {title: loc("application.createContinuationPeriodPermit.confirmation.yes"),
          fn: self.doCreateContinuationPeriodPermit},
        {title: loc("cancel")}
    );
  };

  self.doCreateEncumbrancePermit = function() {
    ajax
      .command("create-encumbrance-permit", {id: self.id()})
      .success(function(data) {
        pageutil.openPage("application", data.id);
      })
      .processing(self.processing)
      .call();
  };

  self.createEncumbrancePermit = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.createEncumbrancePermit.confirmation.title"),
      loc("application.createEncumbrancePermit.confirmation.message"),
      {title: loc("yes"), fn: self.doCreateEncumbrancePermit},
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

  function focusOnElement(id, retryLimit) {
    var targetElem = document.getElementById(id);

    if (!retryLimit) {
      if (targetElem) {
        // last chance: hope that the browser scrolls to somewhere near the focused element.
        targetElem.focus();
      }
      // no more retries and no element: give up
      return;
    }

    var offset = $(targetElem).offset();

    if (!offset || offset.left === 0 || !targetElem) {
      // Element is not yet visible, wait for a short moment.
      // Because of the padding, offset left is never zero when
      // the element is visible.
      setTimeout(_.partial(focusOnElement, id, --retryLimit), 5);
    } else {
      var navHeight = $("nav").first().height() || 0;
      var roomForLabel = (targetElem.nodeName === "UL") ? 0 : 30;
      window.scrollTo(0, offset.top - navHeight - roomForLabel);
      targetElem.focus();
    }
  }

  self.open = function(tab) {
    var suffix = self.infoRequest() ? null : tab;
    pageutil.openApplicationPage(self, suffix);
  };

  self.targetTab.subscribe(function(target) {
    if (target.tab === "requiredFieldSummary") {
      ajax
        .query("fetch-validation-errors", {id: self.id.peek()})
        .success(function (data) {
          self.updateMissingApplicationInfo(data.results);
          checkForNonApprovedDesigners();
        })
        .processing(self.processing)
        .call();
    }
    self.open(target.tab);
    if (target.id) {
      var maxRetries = 10; // quite arbitrary, might need to increase for slower browsers
      focusOnElement(target.id, maxRetries);
    }
  });

  self.changeTab = function(model,event) {
    self.targetTab({tab: $(event.target).closest("[data-target]").attr("data-target"), id: null});
  };

  self.nextTab = function(model,event) {
    self.targetTab({tab: $(event.target).closest("[data-target]").attr("data-target"), id: "applicationTabs"});
  };

  // called from application actions
  self.goToApplicationApproval = function() {
    self.targetTab({tab:"requiredFieldSummary",id:"applicationTabs"});
  };

  self.moveToIncorrectlyFilledRequiredField = function(fieldInfo) {
    AccordionState.set( fieldInfo.document.id, true );
    var targetId = fieldInfo.document.id + "-" + fieldInfo.path.join("-");
    self.targetTab({tab: (fieldInfo.document.type !== "party") ? "info" : "parties", id: targetId});
  };

  self.updateMissingApplicationInfo = function(errors) {
    self.incorrectlyFilledRequiredFields(util.extractRequiredErrors(errors));
    self.fieldWarnings(util.extractWarnErrors(errors));
    fetchApplicationSubmittable();
  };

  function cannotSubmitResponse(data) {
    self.submitErrors(data.errors);
  }

  function fetchApplicationSubmittable() {
    if (lupapisteApp.models.applicationAuthModel.ok("application-submittable")) {
      ajax
        .query("application-submittable", {id: self.id.peek()})
        .success(function() { self.submitErrors([]); })
        .onError("error.cannot-submit-application", cannotSubmitResponse)
        .onError("error.command-illegal-state", self.lightReload)
        .call();
    }
  }

  self.toggleHelp = function(param) {
    self[param](!self[param]());
  };

  self.toAsianhallinta = function() {
    ajax.command("application-to-asianhallinta", {id: self.id(), lang: loc.getCurrentLanguage()})
      .success(function() {
        self.reload();
      })
      .error(function(e) {
        util.showIntegrationError({ltitle: "integration.asianhallinta.title",
                                   ltext: e.text,
                                   details: e.details});
      })
      .processing(self.processing)
      .call();
  };


  function returnToDraftAjax() {
    ajax.command("return-to-draft", {id: self.id(), lang: loc.getCurrentLanguage(), text: self.returnToDraftText()})
      .success(function() {
        self.returnToDraftText("");
        self.reload();
      })
      .error(function() { self.reload(); })
      .fuse(self.stateChanged)
      .processing(self.processing)
      .call();
    return false;
  }

  self.returnToDraftText = ko.observable("");
  self.returnToDraft = function() {
    if (!self.stateChanged()) {
      hub.send("show-dialog", {ltitle: "application.returnToDraft.title",
                               size: "large",
                               component: "textarea-dialog",
                               componentParams: {text: loc("application.returnToDraft.areyousure"),
                                                 yesFn: returnToDraftAjax,
                                                 lyesTitle: "application.returnToDraft.areyousure.confirmation",
                                                 lnoTitle: "cancel",
                                                 textarea: {llabel: "application.returnToDraft.reason",
                                                            rows: 10,
                                                            observable: self.returnToDraftText}}});
    }
  };

  self.showAcceptPersonalInvitationDialog = function() {
    if (self.hasPersonalInvites() && lupapisteApp.models.applicationAuthModel.ok("approve-invite")) {
      hub.send("show-dialog", {ltitle: "application.inviteSend",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "application.inviteDialogText",
                                                 lyesTitle: "applications.approveInvite",
                                                 lnoTitle: "application.showApplication",
                                                 yesFn: approveInvite}});
    }
  };

  self.showAcceptCompanyInvitationDialog = function() {
    var applySubmitRestriction = ko.observable(false);
    if (self.hasCompanyInvites() && lupapisteApp.models.applicationAuthModel.ok("approve-invite")) {
      hub.send("show-dialog", {ltitle: "application.inviteSend",
                               size: "medium",
                               component: "company-approve-invite-dialog",
                               componentParams: {ltext: "application.inviteCompanyDialogText",
                                                 applySubmitRestriction: applySubmitRestriction,
                                                 lyesTitle: "applications.approveInvite",
                                                 lnoTitle: "application.showApplication",
                                                 yesFn: _.partial(approveInvite, "company", {applySubmitRestriction: applySubmitRestriction})}});
    }
  };

  self.showAddPropertyButton = ko.pureComputed( function () {
    var primaryOp = lupapisteApp.models.application.primaryOperation();

    return lupapisteApp.models.applicationAuthModel.ok("create-doc") &&
      _.includes(util.getIn(primaryOp, ["optional"]), "secondary-kiinteistot");
  });

  self.addProperty = function() {
    hub.send("show-dialog", {ltitle: "application.dialog.add-property.title",
                             size: "medium",
                             component: "add-property-dialog"});
  };

  self.externalApi = {
    enabled: ko.pureComputed(function() { return lupapisteApp.models.rootVMO.externalApi.enabled(); }),
    ok: function(fnName) { return lupapisteApp.models.rootVMO.externalApi.ok(fnName); },
    showOnMap: function(model) {
      var permit = externalApiTools.toExternalPermit(model._js);
      hub.send("external-api::show-on-map", permit);
    },
    openApplication: function(model) {
      var permit = externalApiTools.toExternalPermit(model._js);
      hub.send("external-api::open-application", permit);
    }};

  // Saved from the old LUPAPISTE.AttachmentsTabModel, used in info request
  self.deleteSingleAttachment = function(a) {
    var versions = util.getIn(a, ["versions"]);
    var doDelete = function() {
      lupapisteApp.services.attachmentsService.removeAttachment(util.getIn(a, ["id"]));
      return false;
    };
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: doDelete}});
  };

  self.verdictPostfix = function () {
    if (this.permitSubtype() === "sijoitussopimus") {
      return ".sijoitussopimus";
    } else {
      return "";
    }
  };

  self.copy = function() {
    pageutil.openPage("copy",  self.id());
  };

  self.propertyFormationApp = function() {
    pageutil.openPage("property-formation-app", self.id());
  };

  self.createDiggingPermit = function() {
    pageutil.openPage("create-digging-permit",  self.id());
  };

  self.canBeCopied = ko.observable(false);
  hub.subscribe("application-model-updated", function() {
    ajax
      .query("application-copyable", {"source-application-id": self.id()})
      .success(function() {
        self.canBeCopied(true);
      })
      .error(function() {
        self.canBeCopied(false);
      })
      .call();
    return false;
  });

  self.requiredFieldSummaryButtonVisible = ko.pureComputed(function() {
    return _.includes(["draft", "open", "submitted", "complementNeeded"], ko.unwrap(self.state));
  });

  self.requiredFieldSummaryButtonKey = ko.pureComputed(function() {
    if (self.isArchivingProject()) {
      return "archivingProject.tabRequiredFieldSummary";
    } else if (lupapisteApp.models.applicationAuthModel.ok("approve-application") ||
               lupapisteApp.models.applicationAuthModel.ok("update-app-bulletin-op-description")) {
      return "application.tabRequiredFieldSummary.afterSubmitted";
    } else {
      return "application.tabRequiredFieldSummary";
    }
  });

  self.requiredFieldSummaryButtonHighlight = ko.pureComputed(function() {
    return (lupapisteApp.models.applicationAuthModel.ok("approve-application")
                     || lupapisteApp.models.applicationAuthModel.ok("update-app-bulletin-op-description")
                     || _.includes(["draft", "open"], ko.unwrap(self.state)));
  });

  self.canDraw = lupapisteApp.models.applicationAuthModel.okObservable("save-application-drawings");

  self.planInfoDisabled = ko.pureComputed(function() {
    return self.organizationMeta() ? self.organizationMeta().planInfoDisabled(): true;
  });

  self.gotoLinkPermitCard = _.partial( hub.send,
                                       "cardService::select",
                                       {card: "add-link-permit",
                                        deck: "summary"});

  self.doRemoveLinkPermit = function(linkPermitId) {
    ajax.command("remove-link-permit-by-app-id", {id: self.id(), linkPermitId: linkPermitId})
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        repository.load(self.id());
      })
      .call();
  };

  self.removeSelectedLinkPermit = function(linkPermit) {
    hub.send("show-dialog", {ltitle: "linkPermit.remove.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {text: loc("linkPermit.remove.message", linkPermit.id()),
                                               yesFn: _.partial(self.doRemoveLinkPermit, linkPermit.id())}});
  };

  function callRemoveBuildings () {
    return function () {
      ajax
        .command("remove-buildings", {id: self.id()})
        .success(function() {
          self.reload();
        })
        .call();
      return false;
    };
  }

  self.removeBuildings = function() {
    hub.send("show-dialog", {ltitle: "application.remove.buildings",
      size: "medium",
      component: "yes-no-dialog",
      componentParams: {text: loc("areyousure.removeBuildings"),
        yesFn: callRemoveBuildings(),
        lyesTitle: "yes",
        lnoTitle: "no"}});
  };

  // src/lupapalvelu/operations.clj: operation-tree-for-R
  var BUILDING_OPERATION_NAMES = [
    "kerrostalo-rivitalo",
    "pientalo",
    "vapaa-ajan-asuinrakennus",
    "varasto-tms",
    "teollisuusrakennus",
    "muu-uusi-rakentaminen",
    "kerrostalo-rt-laaj",
    "pientalo-laaj",
    "vapaa-ajan-rakennus-laaj",
    "talousrakennus-laaj",
    "teollisuusrakennus-laaj",
    "muu-rakennus-laaj",
    "kayttotark-muutos",
    "sisatila-muutos",
    "julkisivu-muutos",
    "markatilan-laajentaminen",
    "linjasaneeraus",
    "parveke-tai-terassi",
    "perus-tai-kant-rak-muutos",
    "takka-tai-hormi",
    "jakaminen-tai-yhdistaminen",
  ];

  function isBuildingOperation(operation) {
    return _.includes(BUILDING_OPERATION_NAMES, operation.name);
  }

  var STRUCTURE_OPERATION_NAMES = [
    "auto-katos",
    "masto-tms",
    "mainoslaite",
    "aita",
    "maalampo",
    "jatevesi"
  ];

  function isStructureOperation(operation) {
    return _.includes(STRUCTURE_OPERATION_NAMES, operation.name);
  }

  // Looks up the given operation's tunnus and adds it to the operation.
  function addTunnus(operation) {
    // The operation's tunnus is stored in a separate document
    var document = _.find(self._js.documents, function(d){
      return _.get(d, "schema-info.op.id") === operation.id;
    });
    operation.tunnus = _.get(document, "data.tunnus.value");
    return operation;
  }

  var getOperations = ko.pureComputed(function() {
    // Operations can either be stored in `primaryOperation` or in the
    // `secondaryOperations` array. Sometimes they can be undefined, in which
    // case we return no operations.
    var primaryOperation = ko.toJS(self.primaryOperation);
    var secondaryOperations = ko.toJS(self.secondaryOperations);

    if (_.isUndefined(primaryOperation) ||
        _.isUndefined(secondaryOperations)) {
      return [];
    } else {
      return _
        .chain([primaryOperation])
        .concat(secondaryOperations)
        .map(addTunnus);
    }
  });

  // Returns the operation's tunnus and/or description in a single string or
  // `null` if both are missing.
  function tunnusAndOrDescription(operation) {
    if (operation.tunnus && operation.description) {
      return operation.tunnus + ": " + operation.description;
    } else if (operation.tunnus) {
      return operation.tunnus;
    } else if (operation.description){
      return operation.description;
    } else {
      return null;
    }
  }

  // The date picker component represents dates as JS `Date` objects while the
  // backend uses timestamps. The following functions convert between them,
  // handling `null` and `undefined` values gracefully (`null`s are used for
  // absent values in both representations).

  function dateFromTimestamp(x) {
    return x ? new Date(x) : null;
  }

  function timestampFromDate(x) {
    return x ? x.getTime() : null;
  }

  // A view model for editing an operation via the "Buildings and structures"
  // table.
  function BuildingModel(operation) {
    this.operation = {
      id: operation.id,
      nameKey: operation.name + "._group_label",
      description: tunnusAndOrDescription(operation),
      extinct: ko.observable(dateFromTimestamp(operation.extinct))
    };
    this.extinctInputValue = ko.observable("");
    this.isInEditMode = ko.observable(false);
  }

  BuildingModel.prototype.edit = function(){
    this.extinctInputValue(this.operation.extinct());
    this.isInEditMode(true);
  };

  BuildingModel.prototype.save = function(){
    this.operation.extinct(this.extinctInputValue());
    this.isInEditMode(false);
    ajax.command("set-building-extinct",
                 // Note that `self` refers to the surrounding
                 // `ApplicationModel`!
                 {id: self.id(),
                  lang: loc.getCurrentLanguage(),
                  operationId: this.operation.id,
                  extinct: timestampFromDate(this.operation.extinct())})
      .success(util.showSavedIndicator)
      .processing(self.processing)
      .call();
  };

  BuildingModel.prototype.cancel = function(){
    this.isInEditMode(false);
  };

  self.buildings = ko.pureComputed(function() {
    return _
      .chain(getOperations())
      .filter(function(b){
        // Buildings and structures are handled identically, so for simplicity's
        // sake we only talk about buildings everywhere except for this place,
        // where we choose to include structure operations in our list of
        // building operations.
        return isBuildingOperation(b) || isStructureOperation(b);
      })
      .map(function(operation){
        return new BuildingModel(operation);
      })
      .value();
  });

};
