;(function() {
  "use strict";

  var isInitializing = true;
  var currentId = null;
  var authorizationModel = lupapisteApp.models.applicationAuthModel;
  var applicationModel = lupapisteApp.models.application;
  var changeLocationModel = new LUPAPISTE.ChangeLocationModel();
  var addLinkPermitModel = new LUPAPISTE.AddLinkPermitModel();
  var constructionStateChangeModel = new LUPAPISTE.ModalDatepickerModel();

  constructionStateChangeModel.openConstructionStartDialog = _.partial(
      constructionStateChangeModel.openWithConfig,
      {commandName         : "inform-construction-started",
       checkIntegrationAvailability: false,
       dateParameter       : "startedTimestampStr",
       extraParameters     : {lang: loc.getCurrentLanguage()},
       dateSelectorLabel   : "constructionStarted.startedDate",
       dialogHeader        : "constructionStarted.dialog.header",
       dialogHelpParagraph : "constructionStarted.dialog.helpParagraph",
       dialogButtonSend    : "constructionStarted.dialog.continue",
       areYouSureMessage   : "constructionStarted.dialog.areyousure.message"});
  constructionStateChangeModel.openConstructionReadyDialog = _.partial(
      constructionStateChangeModel.openWithConfig,
      {commandName         : "inform-construction-ready",
       dateParameter       : "readyTimestampStr",
       extraParameters     : {lang: loc.getCurrentLanguage()},
       dateSelectorLabel   : "constructionReady.readyDate",
       dialogHeader        : "constructionReady.dialog.header",
       dialogHelpParagraph : "constructionReady.dialog.helpParagraph",
       dialogButtonSend    : "constructionReady.dialog.continue",
       areYouSureMessage   : "constructionReady.dialog.areyousure.message"});
  constructionStateChangeModel.openBuildingConstructionStartDialog = function(building) {
    constructionStateChangeModel.openWithConfig(
        {commandName         : "inform-building-construction-started",
         checkIntegrationAvailability: true,
         dateParameter       : "startedDate",
         extraParameters     : {buildingIndex: building.index(), lang: loc.getCurrentLanguage()},
         dateSelectorLabel   : "building.constructionStarted.startedDate",
         dialogHeader        : "application.beginConstructionOf",
         dialogHelpParagraph : "building.constructionStarted.dialog.helpParagraph",
         dialogButtonSend    : "constructionStarted.dialog.continue",
         areYouSureMessage   : "building.constructionStarted.dialog.areyousure.message"}, applicationModel);
    return false;
  };


  var inviteModel = new LUPAPISTE.InviteModel();
  var verdictModel = new LUPAPISTE.VerdictsModel();
  var signingModel = new LUPAPISTE.SigningModel("#dialog-sign-attachments", true);
  var verdictAttachmentPrintsOrderModel = new LUPAPISTE.VerdictAttachmentPrintsOrderModel();
  var verdictAttachmentPrintsOrderHistoryModel = new LUPAPISTE.VerdictAttachmentPrintsOrderHistoryModel();
  var addPartyModel = new LUPAPISTE.AddPartyModel();
  var createTaskController = LUPAPISTE.createTaskController;
  var mapModel = new LUPAPISTE.MapModel(authorizationModel);
  var foremanModel = new LUPAPISTE.ForemanModel();

  var authorities = ko.observableArray([]);
  var tosFunctions = ko.observableArray([]);
  var hasEditableDocs = ko.observable();

  var accordian = function(data, event) { accordion.toggle(event); };

  function updateWindowTitle(newTitle) {
    lupapisteApp.setTitle(newTitle || util.getIn(applicationModel, ["_js", "title"]));
  }

  function updatePermitSubtype(value) {
    if (isInitializing || !authorizationModel.ok("change-permit-sub-type")) { return; }

    ajax.command("change-permit-sub-type", {id: currentId, permitSubtype: value})
      .success(function(resp) {
        util.showSavedIndicator(resp);
        applicationModel.lightReload();
      })
      .onError("error.missing-parameters", function(resp) {
        util.showSavedIndicator(resp);
        applicationModel.lightReload();
      })
      .call();
  }
  applicationModel.permitSubtype.subscribe(function(v) { updatePermitSubtype(v); });

  var updateMetadataFields = function(application) {
    if (!_.isEmpty(application.metadata)) {
      applicationModel.metadata(ko.mapping.fromJS(application.metadata));
    } else {
      applicationModel.metadata({});
    }
    if (!_.isEmpty(application.processMetadata)) {
      applicationModel.processMetadata(ko.mapping.fromJS(application.processMetadata));
    } else {
      applicationModel.processMetadata({});
    }
  };

  ko.computed(function() {
    var value = applicationModel.tosFunction();
    var functions = tosFunctions.peek();
    if (!isInitializing && value && !_.isEmpty(functions) && authorizationModel.ok("set-tos-function-for-application")) {
      ajax
        .command("set-tos-function-for-application", {id: currentId, functionCode: value})
        .success(function() {
          repository.load(currentId, applicationModel.pending, updateMetadataFields);
          LUPAPISTE.ModalDialog.showDynamicOk(loc("application.tosMetadataWasResetTitle"), loc("application.tosMetadataWasReset"));
        })
        .call();
    }
  });


  ko.computed(function(){
    var enabled = applicationModel.optionMunicipalityHearsNeighbors();
    if (!isInitializing) {
      ajax.command("set-municipality-hears-neighbors", {id: currentId, enabled: enabled})
      .success(function() {
        applicationModel.reload();
        hub.send("indicator", {style: "positive"});
      })
      .error(util.showSavedIndicator)
      .processing(applicationModel.processing)
      .call();
    }
  });

  function refreshAuthoritiesSelectList(appId) {
    if (authorizationModel.ok("assign-application")) {
      ajax.query("application-authorities", {id: appId})
        .success(function(res) {
          var auths = res.authorities || [];
          authorities(auths);
        })
        .pending(applicationModel.pending)
        .call();
    } else {
      authorities([]);
    }

  }

  hub.subscribe("application-model-updated", function(event) {
    refreshAuthoritiesSelectList(event.applicationId);
  });

  function initAvailableTosFunctions(organizationId) {
    tosFunctions([]);
    if (authorizationModel.ok("available-tos-functions")) {
      ajax
        .query("available-tos-functions", {organizationId: organizationId})
        .success(function(data) {
          tosFunctions(data.functions);
        })
        .call();
    }
  }

  function refreshAuthorizationModel() {
    if (currentId) {
      authorizationModel.refresh({id: currentId});
    } else {
      authorizationModel.setData({});
    }
  }

  function resetApplication() {
    var app = _.merge(LUPAPISTE.EmptyApplicationModel(), {});

    // Plain data
    applicationModel._js = app;
    // Update observables
    var mappingOptions = {ignore: ["documents", "buildings", "verdicts", "transfers", "options"]};
    ko.mapping.fromJS(app, mappingOptions, applicationModel);
  }

  function initWarrantyDates(app) {
    if (app.warrantyEnd) {
      app.warrantyEnd = new Date(app.warrantyEnd);
    }

    if (app.warrantyStart) {
      app.warrantyStart = new Date(app.warrantyStart);
    }
  }

  function subscribeWarrantyDates(app, applicationModel) {
    applicationModel.warrantyEnd.subscribe(function (value) {
      if (!isInitializing && value != null) {
        var ms = new Date(moment(value)).getTime();
        ajax
          .command("change-warranty-end-date",
            {id: app.id, endDate: ms})
          .success ( function() {
            hub.send("indicator-icon", {style: "positive"});
          })
          .call();
      }
    });

    applicationModel.warrantyStart.subscribe(function (value) {
      if (!isInitializing && value != null) {
        var ms = new Date(moment(value)).getTime();
        ajax
          .command("change-warranty-start-date",
            {id: app.id, startDate: ms})
          .success ( function() {
            hub.send("indicator-icon", {style: "positive"});
          })
          .call();
      }
    });
  }

  function documentIsEditable(doc) {
    return _(doc.allowedActions).map(function(v,k) { return v.ok && k; }).includes("update-doc");
  }

  function showApplication(applicationDetails, lightLoad) {
    isInitializing = true;

    authorizationModel.refreshWithCallback({id: applicationDetails.application.id}, function() {
      // Sensible empty default values for those properties not received from the backend.
      var app = _.merge( LUPAPISTE.EmptyApplicationModel(), applicationDetails.application);

      // Plain data
      applicationModel._js = app;

      initWarrantyDates(app);

      // Update observables
      var mappingOptions = {ignore: ["documents", "buildings", "verdicts", "transfers", "options"]};
      ko.mapping.fromJS(app, mappingOptions, applicationModel);
      applicationModel.stateChanged(false);

      // Invite
      inviteModel.setApplicationId(app.id);

      // Verdict details
      verdictModel.refresh(app);

      // Map
      mapModel.refresh(app);

      foremanModel.refresh(app);

      // Operations
      applicationModel.operationsCount(_.map(_.countBy(app.secondaryOperations, "name"), function(v, k) { return {name: k, count: v}; }));

      verdictAttachmentPrintsOrderModel.refresh();
      verdictAttachmentPrintsOrderHistoryModel.refresh();

      // permit subtypes
      applicationModel.permitSubtypes(applicationDetails.permitSubtypes);

      // Organization's TOS functions
      initAvailableTosFunctions(applicationDetails.application.organization);

      // Mark-seen
      if (applicationModel.infoRequest() && authorizationModel.ok("mark-seen")) {
        ajax.command("mark-seen", {id: app.id, type: "comments"}).error(_.noop).call();
      }

      // Documents
      var partyDocs = _(app.documents).filter(util.isPartyDoc).sortBy(util.getDocumentOrder).value();
      var nonpartyDocs = _(app.documents).reject(util.isPartyDoc).sortBy(util.getDocumentOrder).value();
      var editableDocs = _.filter(nonpartyDocs, documentIsEditable);
      var uneditableDocs = _.reject(nonpartyDocs, documentIsEditable);

      var editableDocErrors = _.map(editableDocs, function(doc) { return doc.validationErrors; });
      var partyDocErrors = _.map(partyDocs, function(doc) { return doc.validationErrors; });

      hasEditableDocs(!_.isEmpty(editableDocs));

      if (lupapisteApp.services.accordionService) {
        lupapisteApp.services.accordionService.setDocuments(app.documents);
        lupapisteApp.services.accordionService.authorities = authorities;
      }

      applicationModel.updateMissingApplicationInfo(editableDocErrors.concat(partyDocErrors));
      if (!lightLoad) {
        var devMode = LUPAPISTE.config.mode === "dev";
        var isAuthority = lupapisteApp.models.currentUser.isAuthority();

        // Parties are always visible
        docgen.displayDocuments("partiesDocgen",
                                app,
                                partyDocs,
                                {dataTestSpecifiers: devMode, accordionCollapsed: isAuthority});

        // info tab is visible in pre-verdict and verdict given states
        if (!applicationModel.inPostVerdictState()) {
          docgen.displayDocuments("applicationDocgen",
                                  app,
                                  applicationModel.summaryAvailable() ? [] : nonpartyDocs,
                                  {dataTestSpecifiers: devMode, accordionCollapsed: isAuthority});
        } else {
          docgen.clear("applicationDocgen");
        }

        // summary tab is visible in post-verdict and canceled states
        if (applicationModel.summaryAvailable()) {
          docgen.displayDocuments("applicationAndPartiesDocgen",
              app,
              applicationModel.summaryAvailable() ? uneditableDocs : [],
              {dataTestSpecifiers: false, accordionCollapsed: isAuthority});
        } else {
          docgen.clear("applicationAndPartiesDocgen");
        }

        // show or clear construction time documents
        if (applicationModel.inPostVerdictState() && hasEditableDocs()) {
          docgen.displayDocuments("constructionTimeDocgen",
                                  app,
                                  editableDocs,
                                  {dataTestSpecifiers: devMode,
                                   accordionCollapsed: isAuthority});
        } else {
          docgen.clear("constructionTimeDocgen");
        }
      }

      // Options
      applicationModel.optionMunicipalityHearsNeighbors(util.getIn(app, ["options", "municipalityHearsNeighbors"]));

      // Indicators
      function sumDocIndicators(sum, doc) {
        return sum + app.documentModificationsPerDoc[doc.id];
      }

      applicationModel.nonpartyDocumentIndicator(_.reduce(nonpartyDocs, sumDocIndicators, 0));
      applicationModel.partyDocumentIndicator(_.reduce(partyDocs, sumDocIndicators, 0));

      var pendingCalendarNotifications = _.sortBy(_.filter(app.reservations,
        function (r) {
         return _.includes(r["action-required-by"], lupapisteApp.models.currentUser.id());
        }), "startTime");

      pendingCalendarNotifications = _.map(pendingCalendarNotifications,
        function(n) {
          n.acknowledged = ko.observable("none");
          return n;
        });

      applicationModel.calendarNotificationsPending(
        _.transform(
          _.groupBy(pendingCalendarNotifications, function(n) { return moment(n.startTime).startOf("day").valueOf(); }),
          function (result, value, key) {
            return result.push({ day: _.parseInt(key),
                                 notifications: _.transform(value, function(acc, n) {
                                                  n.participantsText = _.map(n.participants, function (p) { return util.partyFullName(p); }).join(", ");
                                                  acc.push(n);
                                                }, [])});

          }, []));
      applicationModel.calendarNotificationIndicator(pendingCalendarNotifications.length);

      subscribeWarrantyDates(app, applicationModel);

      isInitializing = false;
      pageutil.hideAjaxWait();

      hub.send("application-model-updated", {applicationId: app.id});
    });
  }

  hub.subscribe({eventType: "dialog-close", id: "dialog-valtuutus"}, function() {
    inviteModel.reset();
  });

  // tabs
  var selectedTabName = ko.observable();
  var selectedTab = "";
  var tabFlow = false;
  hub.subscribe("set-debug-tab-flow", function(e) {
    tabFlow = e.value;
    $(".tab-content").show(0,function() { selectTab(selectedTab); });
  });

  function openTab(id) {
    // old conversation tab opens both info tab and side panel
    if (_.includes(["conversation", "notice"], id)) {
      var target = id;
      id = "info"; // info tab is shown + side-panel
      if (!$("#" + target + "-panel").is(":visible")) {
        $("#open-" + target + "-side-panel").click();
      }
    }
    if(tabFlow) {
      $("html, body").animate({ scrollTop: $("#application-"+id+"-tab").offset().top}, 100);
    } else {
      $(".tab-content").hide();
      $("#application-"+id+"-tab").fadeIn();
    }
  }

  function selectTab(tab) {
    selectedTabName(tab);
    openTab(tab);
    selectedTab = tab; // remove after tab-spike

    setTimeout(function() {
      var tabMeta = {"conversation": {type: "comments",   model: applicationModel.unseenComments},
                      "statement":   {type: "statements", model: applicationModel.unseenStatements},
                      "verdict":     {type: "verdicts",   model: applicationModel.unseenVerdicts}};
      // Mark comments seen after a second
      if (tabMeta[tab] && currentId && authorizationModel.ok("mark-seen")) {
        ajax.command("mark-seen", {id: currentId, type: tabMeta[tab].type})
          .success(function() {tabMeta[tab].model(0);})
          .error(_.noop)
          .call();
      }}, 1000);
  }

  function initPage(kind, e) {
    var newId = e.pagePath[0];
    var tab = e.pagePath[1];
    updateWindowTitle();
    if (newId === currentId && tab) {
      selectTab(tab);
    } else {
      hub.send("track-click", {category:"Applications", label: kind, event:"openApplication"});
      pageutil.showAjaxWait();
      currentId = newId;

      repository.load(currentId, applicationModel.pending, function(application) {

        updateMetadataFields(application);

        var fallbackTab = function(application) {
          if (application.inPostVerdictState) {
            if (authorizationModel.ok("tasks-tab-visible")) {
              return "tasks";
            } else {
              return "applicationSummary";
            }
          } else {
            return "info";
          }
        };
        selectTab(tab || fallbackTab(application));
      });
    }
  }

  hub.onPageLoad("application", _.partial(initPage, "application"));
  hub.onPageLoad("inforequest", _.partial(initPage, "inforequest"));
  hub.onPageUnload( "application", function() {
    if( currentId && !_.includes( _.get( window, "location.hash"),
                                  currentId)) {
      currentId = null;
      resetApplication();
    }
  });

  hub.subscribe("application-loaded", function(e) {
    showApplication(e.applicationDetails, e.lightLoad);
    updateWindowTitle(e.applicationDetails.application.title);
  });

  // User details can affect what she can do to the application
  hub.subscribe("reload-current-user", refreshAuthorizationModel);

  function NeighborStatusModel() {
    var self = this;

    self.state = ko.observable();
    self.created = ko.observable();
    self.message = ko.observable();
    self.firstName = ko.observable();
    self.lastName = ko.observable();
    self.userid = ko.observable();

    self.init = function(neighbor) {
      var l = _.last(neighbor.status());
      var u = l.vetuma || l.user;
      return self
        .state(l.state())
        .created(l.created())
        .message(l.message && l.message())
        .firstName(u.firstName && u.firstName())
        .lastName(u.lastName && u.lastName())
        .userid(u.userid && u.userid());
    };

    self.open = function() { LUPAPISTE.ModalDialog.open("#dialog-neighbor-status"); return self; };
  }

  var neighborStatusModel = new NeighborStatusModel();

  var neighborActions = {
    manage: function(application) {
      pageutil.openPage("neighbors", application.id());
      return false;
    },
    markDone: function(neighbor) {
      ajax
        .command("neighbor-mark-done", {id: currentId, neighborId: neighbor.id(), lang: loc.getCurrentLanguage()})
        .complete(_.partial(repository.load, currentId, _.noop))
        .call();
    },
    statusCompleted: function(neighbor) {
      return _.includes(["mark-done", "response-given-ok", "response-given-comments"], _.last(neighbor.status()).state());
    },
    showStatus: function(neighbor) {
      neighborStatusModel.init(neighbor).open();
      return false;
    }
  };

  function SendNeighborEmailModel() {
    var self = this;

    self.id = ko.observable();
    self.neighborId = ko.observable();
    self.propertyId = ko.observable();
    self.name = ko.observable();
    self.email = ko.observable();

    self.ok = ko.computed(function() {
      return util.isValidEmailAddress(self.email());
    });

    self.open = function(neighbor) {
      self
        .id(applicationModel.id())
        .neighborId(neighbor.id())
        .propertyId(neighbor.propertyId())
        .name(neighbor.owner.name())
        .email(neighbor.owner.email());
      LUPAPISTE.ModalDialog.open("#dialog-send-neighbor-email");
    };

    var paramNames = ["id", "neighborId", "propertyId", "name", "email"];
    function paramValue(paramName) { return self[paramName](); }

    self.send = function() {
      ajax
        .command("neighbor-send-invite", _.zipObject(paramNames, _.map(paramNames, paramValue)))
        .pending(pageutil.makePendingAjaxWait(loc("neighbors.sendEmail.sending")))
        .complete(LUPAPISTE.ModalDialog.close)
        .success(_.partial(repository.load, self.id(), pageutil.makePendingAjaxWait(loc("neighbors.sendEmail.reloading"))))
        .call();
      return false;
    };
  }

  var sendNeighborEmailModel = new SendNeighborEmailModel();

  var statementActions = {
    openStatement: function(model) {
      pageutil.openPage("statement", applicationModel.id() + "/" + model.id());
      return false;
    }
  };

  function CalendarConfigModel() {
    var self = this;
    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());
    self.reservationTypes = ko.observableArray([]);
    self.defaultLocation = ko.observable();
    self.authorities = ko.observableArray([]);
    self.initialized = ko.observable(false);

    self.disposedSubscribe(applicationModel.id, function(id) {
      if (!_.isEmpty(id) && authorizationModel.ok("calendars-enabled")) {
        self.sendEvent("calendarService", "fetchApplicationCalendarConfig", {applicationId: id});
      }
    });

    self.addEventListener("calendarService", "applicationCalendarConfigFetched", function(event) {
      self.initialized(event.authorities.length > 0);
      self.reservationTypes(event.reservationTypes);
      self.defaultLocation(event.defaultLocation);
      self.authorities(event.authorities);
    });
  }

  var calendarConfigModel = new CalendarConfigModel();

  $(function() {
    var bindings = {
      // function to access accordion
      accordian: accordian,
      // observables
      application: applicationModel,
      authorities: authorities,
      hasEditableDocs: hasEditableDocs,
      // models
      addLinkPermitModel: addLinkPermitModel,
      addPartyModel: addPartyModel,
      authorization: authorizationModel,
      changeLocationModel: changeLocationModel,
      constructionStateChangeModel: constructionStateChangeModel,
      createTask: createTaskController,
      invite: inviteModel,
      foreman: foremanModel,
      map: mapModel,
      neighbor: neighborActions,
      neighborStatusModel: neighborStatusModel,
      statementActions: statementActions,
      sendNeighborEmailModel: sendNeighborEmailModel,
      signingModel: signingModel,
      verdictAttachmentPrintsOrderModel: verdictAttachmentPrintsOrderModel,
      verdictAttachmentPrintsOrderHistoryModel: verdictAttachmentPrintsOrderHistoryModel,
      verdictModel: verdictModel,
      selectedTabName: selectedTabName,
      tosFunctions: tosFunctions,
      sidePanelService: lupapisteApp.services.sidePanelService,
      calendarConfig: calendarConfigModel
    };

    $("#application").applyBindings(bindings);
    $("#inforequest").applyBindings(bindings);
    $(changeLocationModel.dialogSelector).applyBindings({changeLocationModel: changeLocationModel});
    $(addLinkPermitModel.dialogSelector).applyBindings({addLinkPermitModel: addLinkPermitModel});
    $(constructionStateChangeModel.dialogSelector).applyBindings({constructionStateChangeModel: constructionStateChangeModel});
    $(signingModel.dialogSelector).applyBindings({signingModel: signingModel, authorization: authorizationModel});
    $(verdictAttachmentPrintsOrderModel.dialogSelector).applyBindings({
      verdictAttachmentPrintsOrderModel: verdictAttachmentPrintsOrderModel,
      authorization: authorizationModel
    });
    $(verdictAttachmentPrintsOrderHistoryModel.dialogSelector).applyBindings({
      verdictAttachmentPrintsOrderHistoryModel: verdictAttachmentPrintsOrderHistoryModel
    });
  });

})();
