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
  var attachmentsTab = new LUPAPISTE.AttachmentsTabModel(signingModel, verdictAttachmentPrintsOrderModel);
  var foremanModel = new LUPAPISTE.ForemanModel();

  var authorities = ko.observableArray([]);
  var tosFunctions = ko.observableArray([]);
  var hasConstructionTimeDocs = ko.observable();

  var accordian = function(data, event) { accordion.toggle(event); };

  var AuthorityInfo = function(id, firstName, lastName) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
  };

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

  function updateTosFunction(value) {
    if (!isInitializing) {
      LUPAPISTE.ModalDialog.showDynamicOk(loc("application.tosMetadataWasResetTitle"), loc("application.tosMetadataWasReset"));
      ajax
        .command("set-tos-function-for-application", {id: currentId, functionCode: value})
        .success(function() {
          repository.load(currentId, applicationModel.pending, updateMetadataFields);
        })
        .call();
    }
  }

  applicationModel.permitSubtype.subscribe(function(v) { updatePermitSubtype(v); });

  applicationModel.tosFunction.subscribe(updateTosFunction);

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

  function initAuthoritiesSelectList(data) {
    var authorityInfos = [];
    _.each(data || [], function(authority) {
      authorityInfos.push(new AuthorityInfo(authority.id, authority.firstName, authority.lastName));
    });
    authorities(authorityInfos);
  }

  function initAvailableTosFunctions(organizationId) {
    if (authorizationModel.ok("available-tos-functions")) {
      ajax
        .query("available-tos-functions", {organizationId: organizationId})
        .success(function(data) {
          tosFunctions(data.functions);
        })
        .call();
    }
  }

  function showApplication(applicationDetails, lightLoad) {
    isInitializing = true;

    authorizationModel.refreshWithCallback({id: applicationDetails.application.id}, function() {
      // Sensible empty default values for those properties not received from the backend.
      var app = _.merge( LUPAPISTE.EmptyApplicationModel(), applicationDetails.application);

      // Plain data
      applicationModel._js = app;

      // Update observables
      var mappingOptions = {ignore: ["documents", "buildings", "verdicts", "transfers", "options"]};
      ko.mapping.fromJS(app, mappingOptions, applicationModel);

      // Invite
      inviteModel.setApplicationId(app.id);

      // Verdict details
      verdictModel.refresh(app, applicationDetails.authorities);

      // Map
      mapModel.refresh(app);

      foremanModel.refresh(app);

      // Operations
      applicationModel.operationsCount(_.map(_.countBy(app.secondaryOperations, "name"), function(v, k) { return {name: k, count: v}; }));

      verdictAttachmentPrintsOrderModel.refresh();
      verdictAttachmentPrintsOrderHistoryModel.refresh();

      attachmentsTab.refresh();

      // authorities
      initAuthoritiesSelectList(applicationDetails.authorities);

      // permit subtypes
      applicationModel.permitSubtypes(applicationDetails.permitSubtypes);

      // Organization's TOS functions
      initAvailableTosFunctions(applicationDetails.application.organization);

      // Mark-seen
      if (applicationModel.infoRequest() && authorizationModel.ok("mark-seen")) {
        ajax.command("mark-seen", {id: app.id, type: "comments"}).error(_.noop).call();
      }

      // Documents
      var constructionTimeDocs = _.filter(app.documents, "schema-info.construction-time");
      var nonConstructionTimeDocs = _.reject(app.documents, "schema-info.construction-time");
      var nonpartyDocs = _.filter(nonConstructionTimeDocs, util.isNotPartyDoc);
      var sortedNonpartyDocs = _.sortBy(nonpartyDocs, util.getDocumentOrder);
      var partyDocs = _.filter(nonConstructionTimeDocs, util.isPartyDoc);
      var sortedPartyDocs = _.sortBy(partyDocs, util.getDocumentOrder);

      var nonpartyDocErrors = _.map(sortedNonpartyDocs, function(doc) { return doc.validationErrors; });
      var partyDocErrors = _.map(sortedPartyDocs, function(doc) { return doc.validationErrors; });

      hasConstructionTimeDocs(!!constructionTimeDocs.length);

      if (lupapisteApp.services.accordionService) {
        lupapisteApp.services.accordionService.setDocuments(app.documents);
      }

      applicationModel.updateMissingApplicationInfo(nonpartyDocErrors.concat(partyDocErrors));
      if (!lightLoad) {
        var devMode = LUPAPISTE.config.mode === "dev";
        var isAuthority = lupapisteApp.models.currentUser.isAuthority();

        // Parties are always visible
        docgen.displayDocuments("partiesDocgen",
            app,
            sortedPartyDocs,
            authorizationModel, {dataTestSpecifiers: devMode, accordionCollapsed: isAuthority});

        // info tab is visible in pre-verdict and verdict given states
        if (!applicationModel.inPostVerdictState()) {
          docgen.displayDocuments("applicationDocgen",
              app,
              applicationModel.summaryAvailable() ? [] : sortedNonpartyDocs,
                  authorizationModel,
                  {dataTestSpecifiers: devMode, accordionCollapsed: isAuthority});
        } else {
          docgen.clear("applicationDocgen");
        }

        // summary tab is visible in post-verdict and canceled states
        if (applicationModel.summaryAvailable()) {
          docgen.displayDocuments("applicationAndPartiesDocgen",
              app,
              applicationModel.summaryAvailable() ? sortedNonpartyDocs : [],
                  authorizationModel,
                  {dataTestSpecifiers: false, accordionCollapsed: isAuthority});
        } else {
          docgen.clear("applicationAndPartiesDocgen");
        }

        // show or clear construction time documents
        if (hasConstructionTimeDocs()) {
          docgen.displayDocuments("constructionTimeDocgen",
              app,
              constructionTimeDocs,
              authorizationModel,
              {dataTestSpecifiers: devMode,
            accordionCollapsed: isAuthority,
            updateCommand: "update-construction-time-doc"});
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
      function sumCalendarIndicators() {
        var res = _.filter(app.reservations,
          function (r) {
            return _.includes(r["action-required-by"], lupapisteApp.models.currentUser.id());
          });
        return res.length;
      }
      applicationModel.nonpartyDocumentIndicator(_.reduce(nonpartyDocs, sumDocIndicators, 0));
      applicationModel.partyDocumentIndicator(_.reduce(partyDocs, sumDocIndicators, 0));

      applicationModel.calendarNotificationIndicator(sumCalendarIndicators());

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
      if (newId !== currentId) { // close sidepanel if it's open
        var sidePanel = $("#side-panel div.content-wrapper > div").filter(":visible");
        if (!_.isEmpty(sidePanel)) {
          var target = sidePanel.attr("id").split("-")[0];
          $("#open-" + target + "-side-panel").click();
        }
      }
      currentId = newId;

      repository.load(currentId, applicationModel.pending, function(application) {

        updateMetadataFields(application);

        var fallbackTab = function(application) {
          if (application.inPostVerdictState) {
            var name = application.primaryOperation.name;
            if (name) {
              return name.match(/tyonjohtaja/) ? "applicationSummary" : "tasks";
            } else {
              return "tasks";
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

  hub.subscribe("application-loaded", function(e) {
    showApplication(e.applicationDetails, e.lightLoad);
    updateWindowTitle(e.applicationDetails.application.title);
  });


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


  $(function() {
    var bindings = {
      // function to access accordion
      accordian: accordian,
      // observables
      application: applicationModel,
      authorities: authorities,
      hasConstructionTimeDocs: hasConstructionTimeDocs,
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
      attachmentsTab: attachmentsTab,
      selectedTabName: selectedTabName,
      tosFunctions: tosFunctions,
      sidePanelService: lupapisteApp.services.sidePanelService
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
    attachmentsTab.attachmentTemplatesModel.init();
  });

})();
