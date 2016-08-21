LUPAPISTE.EmptyApplicationModel = function() {
  "use strict";
  return {startedBy: {firstName: "", lastName: ""},
          closedBy: {firstName: "", lastName: ""}};
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
  self.submitted = ko.observable();
  self.location = ko.observable();
  self.municipality = ko.observable();
  self.permitType = ko.observable("R");
  self.propertyId = ko.observable();
  self.title = ko.observable();
  self.created = ko.observable();
  self.modified = ko.observable();
  self.started = ko.observable();
  self.startedBy = fullNameInit();
  self.closed = ko.observable();
  self.closedBy = fullNameInit();
  self.attachments = ko.observable([]);
  self.hasAttachment = ko.computed(function() {
    return _.some((ko.toJS(self.attachments) || []), function(a) {return a.versions && a.versions.length;});
  });
  self.address = ko.observable();
  self.secondaryOperations = ko.observable();
  self.primaryOperation = ko.observable();
  self.allOperations = ko.observable();

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

  self.operationsCount = ko.observable();
  self.applicant = ko.observable();
  self.assignee = ko.observable();
  self.applicantPhone = ko.observable();
  self.authority = ko.observable({});
  self.neighbors = ko.observable([]);
  self.statements = ko.observable([]);
  self.tasks = ko.observable([]);
  self.tosFunction = ko.observable(null);
  self.metadata = ko.observable();
  self.processMetadata = ko.observable();

  // Options
  self.optionMunicipalityHearsNeighbors = ko.observable(false);
  self.optionMunicipalityHearsNeighborsDisabled = ko.pureComputed(function() {
    return !lupapisteApp.models.applicationAuthModel.ok("set-municipality-hears-neighbors");
  });
  self.municipalityHearsNeighborsVisible = ko.pureComputed( function() {
    return lupapisteApp.models.applicationAuthModel.ok( "municipality-hears-neighbors-visible");
  });

  // Application indicator metadata fields
  self.unseenStatements = ko.observable();
  self.unseenVerdicts = ko.observable();
  self.unseenComments = ko.observable();
  self.attachmentsRequiringAction = ko.observable();

  // Application metadata fields
  self.inPostVerdictState = ko.observable(false);
  self.stateSeq = ko.observable([]);
  self.currentStateInSeq = ko.pureComputed(function() {return _.includes(self.stateSeq(), self.state());});
  self.inPostSubmittedState = ko.observable(false); // TODO: remove
  self.vendorBackendId = ko.observable(); // TODO: remove
  self.applicantPhone = ko.observable();
  self.organizationMeta = ko.observable();
  self.neighbors = ko.observable([]);
  self.submitErrors = ko.observableArray();

  self.organization = ko.observable([]);

  self.asianhallintaEnabled = ko.pureComputed(function() {
    return self.organizationMeta() ? self.organizationMeta().asianhallinta() : false;
  });
  self.organizationLinks = ko.pureComputed(function() {
    return self.organizationMeta() ? self.organizationMeta().links() : "";
  });
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
  self.tags = ko.observable();
  self.comments = ko.observable([]);

  self.summaryAvailable = ko.pureComputed(function() {
    return self.inPostVerdictState() || self.state() === "canceled";
  });

  self.openTask = function( taskId ) {
    hub.send( "scrollService::push");
    taskPageController.setApplicationModelAndTaskId(self._js, taskId);
    pageutil.openPage("task",  self.id() + "/" + taskId);
  };

  self.taskGroups = ko.pureComputed(function() {
    var tasks = ko.toJS(self.tasks) || [];
    // TODO query without foreman tasks
    tasks = _.filter(tasks, function(task) {
      return !_.includes( ["task-vaadittu-tyonjohtaja", "task-katselmus", "task-katselmus-backend"],
                          task["schema-info"].name);
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
            task.openTask = _.partial( self.openTask, task.id);
            task.statusName = LUPAPISTE.statuses[task.state] || "unknown";

            return task;
          })};})
      .sortBy("order")
      .valueOf();
  });

  self.primaryOperationName = ko.pureComputed(function() {
    var op = ko.unwrap(self.primaryOperation());
    if (op) {
      return "operations." + ko.unwrap(op.name);
    }
    return "";
  });

  self.foremanTasks = ko.observable();

  self.nonpartyDocumentIndicator = ko.observable(0);
  self.partyDocumentIndicator = ko.observable(0);

  self.calendarNotificationIndicator = ko.observable(0);
  self.calendarNotificationsPending = ko.observableArray([]);

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

  self.toBackingSystem = function() {
    window.open("/api/raw/redirect-to-vendor-backend?id=" + self.id(), "backend");
  };

  self.updateInvites = function() {
    invites.getInvites(function(data) {
      self.invites(_.filter(data.invites, function(invite) {
        return invite.application.id === self.id();
      }));
      if (self.hasInvites()) {
        self.showAcceptInvitationDialog();
      }
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

  self.missingRequiredAttachments = ko.observable([]);
  self.hasMissingRequiredAttachments = ko.pureComputed(function() {
    return self.missingRequiredAttachments() && self.missingRequiredAttachments().length > 0;
  });

  self.missingSomeInfo = ko.pureComputed(function() {
    return self.hasFieldWarnings() || self.hasIncorrectlyFilledRequiredFields() || self.hasMissingRequiredAttachments();
  });

  self.submitButtonEnabled = ko.pureComputed(function() {
    return !self.processing() && !self.hasInvites() && (!self.requiredFieldsFillingObligatory() || !self.missingSomeInfo()) && _.isEmpty(self.submitErrors());
  });


    self.reload = function() {
    self.submitErrors([]);
    repository.load(self.id());
  };

  self.lightReload = function() {
    repository.load(self.id(), undefined, undefined, true);
  };

  self.roles = ko.computed(function() {
    var withRoles = function(r, i) {
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
    };
    var pimped = _.reduce(self.auth(), withRoles, {});
    return _.values(pimped);
  });

  self.openOskariMap = function() {
    var featureParams = ["addPoint", "addArea", "addLine", "addCircle", "addEllipse"];
    var featuresEnabled = lupapisteApp.models.applicationAuthModel.ok("save-application-drawings") ? 1 : 0;
    var features = _.map(featureParams, function (f) {return f + "=" + featuresEnabled;}).join("&");
    var params = ["build=" + LUPAPISTE.config.build,
                  "id=" + self.id(),
                  "coord=" + self.location().x() + "_" + self.location().y(),
                  "zoomLevel=12",
                  "lang=" + loc.getCurrentLanguage(),
                  "municipality=" + self.municipality(),
                  features];

    var url = "/oskari/fullmap.html?" + params.join("&");
    window.open(url);
    hub.send("track-click", {category:"Application", label:"map", event:"openOskariMap"});
  };

  self.submitApplication = function() {
    hub.send("track-click", {category:"Application", label:"submit", event:"submitApplication"});
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.submit.areyousure.title"),
      loc("application.submit.areyousure.message"),
      {title: loc("yes"),
       fn: function() {
            ajax.command("submit-application", {id: self.id()})
           .success( self.reload)
           .onError("error.cannot-submit-application", cannotSubmitResponse)
           .processing(self.processing)
           .call();
         hub.send("track-click", {category:"Application", label:"submit", event:"applicationSubmitted"});
         return false;
       }},
      {title: loc("no")}
    );
    hub.send("track-click", {category:"Application", label:"cancel", event:"applicationSubmitCanceled"});
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
      .processing(self.processing)
      .call();
    return false;
  };

  self.convertToApplication = function() {
    ajax.command("convert-to-application", {id: self.id()})
      .success(function() {
        pageutil.openPage("application", self.id());
      })
      .processing(self.processing)
      .call();
      hub.send("track-click", {category:"Inforequest", label:"", event:"convertToApplication"});
    return false;
  };

  self.approveApplication = function() {
    var approve = function() {
      ajax.command("approve-application", {id: self.id(), lang: loc.getCurrentLanguage()})
        .success(function(resp) {
          self.reload();
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
        .error(function(e) {LUPAPISTE.showIntegrationError("integration.title", e.text, e.details);})
        .processing(self.processing)
        .call();
      hub.send("track-click", {category:"Application", label:"", event:"approveApplication"});
    };

    var checkDesigners = function() {
      var nonApprovedDesigners = _(docgen.nonApprovedDocuments()).filter(function(docModel) {
          return docModel.schema.info.subtype === "suunnittelija";
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
          // Escape HTML special chars
          return "<li>" + _.escape(title + headerDescription) + "</li>";
        })
        .value();

      // All designers have not been approved?
      if (!_.isEmpty(nonApprovedDesigners)) {
        var text = loc("application.designers-not-approved-help") + "<ul>" + nonApprovedDesigners.join("") + "</ul>";
        hub.send("show-dialog", {ltitle: "application.designers-not-approved",
          size: "medium",
          component: "yes-no-dialog",
          componentParams: {text: text, yesFn: approve, lyesTitle: "continue", lnoTitle: "cancel"}});
      } else {
        approve();
      }
    };

    if (!( lupapisteApp.models.applicationAuthModel.ok( "statements-after-approve-allowed")
           || _(self._js.statements).reject("given").isEmpty())) {
      // All statements have not been given
      hub.send("show-dialog", {ltitle: "application.approve.statement-not-requested",
        size: "medium",
        component: "yes-no-dialog",
        componentParams: {ltext: "application.approve.statement-not-requested-warning-text",
          yesFn: checkDesigners}});
    } else {
      checkDesigners();
    }
    return false;
  };

  self.refreshKTJ = function() {
    ajax.command("refresh-ktj", {id: self.id()})
      .success(function() {
        self.reload();
        LUPAPISTE.ModalDialog.showDynamicOk(loc("integration.title"), loc("application.refreshed"));
      })
      .processing(self.processing)
      .call();
    hub.send("track-click", {category:"Application", label:"", event:"refreshKTJ"});
    return false;
  };

  self.findOwners = function() {
    hub.send("show-dialog", { ltitle: "neighbor.owners.title",
      size: "large",
      component: "neighbors-owners-dialog",
      componentParams: {applicationId: self.id()} });
    hub.send("track-click", {category:"Application", label:"", event:"findOwners"});
    return false;
  };

  self.removeAuth = function(model) {
    var username = model.username();
    hub.send("track-click", {category:"Application", label:"", event:"removeAuth"});
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("areyousure.message"),
      {title: loc("yes"),
       fn:  function() {
         ajax.command("remove-auth", { id: self.id(), username: username})
           .success(self.reload)
           .processing(self.processing)
           .call();
          hub.send("track-click", {category:"Application", label:"", event:"authRemoved"});
         return false;
      }},
      {title: loc("no")}
    );
    hub.send("track-click", {category:"Application", label:"", event:"authRemoveCanceled"});
    return false;
  };

  self.isNotOwner = function(model) {
    return model.role() !== "owner";
  };

  self.userHasRole = function(userModel, role) {
    return _(util.getIn(self.roles()))
      .filter(function(r) { return r.id() === util.getIn(userModel, ["id"]); })
      .invokeMap("role")
      .includes(role);
  };

  self.canSubscribe = function(model) {
    return model.role() !== "statementGiver" &&
           lupapisteApp.models.currentUser &&
           (lupapisteApp.models.currentUser.isAuthority() || lupapisteApp.models.currentUser.id() ===  model.id()) &&
           lupapisteApp.models.applicationAuthModel.ok("subscribe-notifications") &&
           lupapisteApp.models.applicationAuthModel.ok("unsubscribe-notifications");
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
    pageutil.openPage("add-operation", self.id());
    hub.send("track-click", {category:"Application", label:"", event:"addOperation"});
    return false;
  };

  self.cancelInforequest = function() {
    hub.send("track-click", {category:"Inforequest", label:"", event:"cancelInforequest"});
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("areyousure.cancel-inforequest"),
      {title: loc("yes"),
       fn: function() {
        ajax
          .command("cancel-inforequest", {id: self.id()})
          .success(function() {pageutil.openPage("applications");})
          .processing(self.processing)
          .call();
        hub.send("track-click", {category:"Inforequest", label:"", event:"infoRequestCanceled"});
        return false;}},
      {title: loc("no")}
    );
    hub.send("track-click", {category:"Inforequest", label:"", event:"infoRequestCancelCanceled"});
    return false;
  };

  self.cancelText = ko.observable("");

  self.cancelApplication = function() {
    var command = lupapisteApp.models.applicationAuthModel.ok( "cancel-application-authority")
          ? "cancel-application-authority"
          : "cancel-application";
    hub.send("track-click", {category:"Application", label:"", event:"cancelApplication"});
    LUPAPISTE.ModalDialog.setDialogContent(
      $("#dialog-cancel-application"),
      loc("areyousure"),
      loc("areyousure.cancel-application"),
      {title: loc("yes"),
       fn: function() {
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
          .processing(self.processing)
          .call();
        return false;}},
      {title: loc("no")}
    );
    LUPAPISTE.ModalDialog.open("#dialog-cancel-application");
  };

  self.undoCancellation = function() {
    var sendCommand = ajax
                        .command("undo-cancellation", {id: self.id()})
                        .success(function() {
                          repository.load(self.id());
                        })
                        .processing(self.processing);

    hub.send("show-dialog", {ltitle: "application.undoCancellation",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {text: loc("application.undoCancellation.areyousure", loc(util.getPreviousState(self._js))),
                                               yesFn: function() { sendCommand.call(); }}});
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
    hub.send("track-click", {category:"Application", label:"", event:"newOtherAttachment"});
  };

  self.createChangePermit = function() {
    hub.send("track-click", {category:"Application", label:"", event:"createChangePermit"});
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.createChangePermit.areyousure.title"),
      loc("application.createChangePermit.areyousure.message"),
      {title: loc("application.createChangePermit.areyousure.ok"),
       fn: function() {
        ajax
          .command("create-change-permit", {id: self.id()})
          .success(function(data) {
            pageutil.openPage("application", data.id);
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
    hub.send("track-click", {category:"Application", label:"", event:"doCreateContinuationPeriodPermit"});
    ajax
      .command("create-continuation-period-permit", {id: self.id()})
      .success(function(data) {
        pageutil.openPage("application", data.id);
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
    hub.send("track-click", {category:"Application", label:"", event:"resetIndicators"});
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
    self.targetTab({tab: $(event.target).closest("a").attr("data-target"), id: null});
    hub.send("track-click", {category:"Application", label:self.targetTab().tab, event:"changeTab"});
  };

  self.nextTab = function(model,event) {
    self.targetTab({tab: $(event.target).closest("a").attr("data-target"), id: "applicationTabs"});
    hub.send("track-click", {category:"Application", label:self.targetTab().tab, event:"nextTab"});
  };

  self.moveToIncorrectlyFilledRequiredField = function(fieldInfo) {
    AccordionState.set( fieldInfo.document.id, true );
    var targetId = fieldInfo.document.id + "-" + fieldInfo.path.join("-");
    self.targetTab({tab: (fieldInfo.document.type !== "party") ? "info" : "parties", id: targetId});
  };

  self.moveToMissingRequiredAttachment = function(fieldInfo) {
    var targetId = "attachment-row-" + fieldInfo.type["type-group"]() + "-" + fieldInfo.type["type-id"]();
    self.targetTab({tab: "attachments", id: targetId});
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
    self.fieldWarnings(util.extractWarnErrors(errors));
    self.missingRequiredAttachments(extractMissingAttachments(self.attachments()));
    fetchApplicationSubmittable();
  };

  function cannotSubmitResponse(data) {
    self.submitErrors(_.map(data.errors, "text"));
  }

  function fetchApplicationSubmittable() {
    if (lupapisteApp.models.applicationAuthModel.ok("submit-application")) {
      ajax
        .query("application-submittable", {id: self.id.peek()})
        .success(function() { self.submitErrors([]); })
        .onError("error.cannot-submit-application", cannotSubmitResponse)
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
      .error(function(e) {LUPAPISTE.showIntegrationError("integration.asianhallinta.title", e.text, e.details);})
      .processing(self.processing)
      .call();
  };

  self.showAcceptInvitationDialog = function() {
    if (self.hasInvites() && lupapisteApp.models.applicationAuthModel.ok("approve-invite")) {
      hub.send("show-dialog", {ltitle: "application.inviteSend",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "application.inviteDialogText",
                                                 lyesTitle: "applications.approveInvite",
                                                 lnoTitle: "application.showApplication",
                                                 yesFn: self.approveInvite}});
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
    enabled: ko.pureComputed(function() {
      return lupapisteApp.models.rootVMO.externalApiEnabled() &&
             lupapisteApp.models.applicationAuthModel.ok("external-api-enabled");
    }),
    showOnMap: function(model) {
      var permit = externalApiTools.toExternalPermit(model._js);
      hub.send("external-api::show-on-map", permit);
    },
    openApplication: function(model) {
      var permit = externalApiTools.toExternalPermit(model._js);
      hub.send("external-api::open-application", permit);
    }};
};
