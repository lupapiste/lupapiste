;(function() {
  "use strict";

  var isInitializing = true;
  var currentId = null;
  var authorizationModel = authorization.create();
  var applicationModel = new LUPAPISTE.ApplicationModel(authorizationModel);
  var changeLocationModel = new LUPAPISTE.ChangeLocationModel();
  var addLinkPermitModel = new LUPAPISTE.AddLinkPermitModel();
  var constructionStateChangeModel = new LUPAPISTE.ModalDatepickerModel();
  var commentModel = comments.create(true);

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
  var stampModel = new LUPAPISTE.StampModel();
  var signingModel = new LUPAPISTE.SigningModel("#dialog-sign-attachments", true);
  var requestForStatementModel = new LUPAPISTE.RequestForStatementModel();
  var addPartyModel = new LUPAPISTE.AddPartyModel();
  var createTaskController = LUPAPISTE.createTaskController;
  var mapModel = new LUPAPISTE.MapModel(authorizationModel);
  var attachmentsTab = new LUPAPISTE.AttachmentsTabModel(applicationModel);

  var authorities = ko.observableArray([]);
  var permitSubtypes = ko.observableArray([]);

  var inviteCompanyModel = new LUPAPISTE.InviteCompanyModel(applicationModel.id);

  var accordian = function(data, event) { accordion.toggle(event); };


  var AuthorityInfo = function(id, firstName, lastName) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
  };

    //FIXME: why is this?
  function updateAssignee(value) {
    // do not update assignee if page is still initializing
    if (isInitializing) { return; }

    // The right is validated in the back-end. This check is just to prevent error.
    if (!authorizationModel.ok('assign-application')) { return; }

    var assigneeId = value ? value : null;

    ajax.command("assign-application", {id: currentId, assigneeId: assigneeId})
      .success(function() {
        authorizationModel.refresh(currentId);
        })
      .error(function(data) {
        LUPAPISTE.ModalDialog.showDynamicOk(loc("error.dialog.title"), loc(data.text) + ": " + data.id);
      })
      .call();
  }

  function updatePermitSubtype(value){
    if (isInitializing) { return; }

    ajax.command("change-permit-sub-type", {id: currentId, permitSubtype: value})
    .success(function() {
      authorizationModel.refresh(currentId);
      })
    .error(function(data) {
      LUPAPISTE.ModalDialog.showDynamicOk(loc("error.dialog.title"), loc(data.text) + ": " + data.id);
    })
    .call();
  }

  applicationModel.assignee.subscribe(function(v) { updateAssignee(v); });
  applicationModel.permitSubtype.subscribe(function(v){updatePermitSubtype(v);});

  function resolveApplicationAssignee(authority) {
    return (authority) ? new AuthorityInfo(authority.id, authority.firstName, authority.lastName) : null;
  }

  function initAuthoritiesSelectList(data) {
    var authorityInfos = [];
    _.each(data || [], function(authority) {
      authorityInfos.push(new AuthorityInfo(authority.id, authority.firstName, authority.lastName));
    });
    authorities(authorityInfos);
  }


  function showApplication(applicationDetails) {
    isInitializing = true;

    authorizationModel.refreshWithCallback({id: applicationDetails.application.id}, function() {
      var app = applicationDetails.application;

      // Plain data
      applicationModel._js = app;

      // Update observables
      ko.mapping.fromJS(app, {}, applicationModel);

      // Invite
      inviteModel.setApplicationId(app.id);

      // Comments
      commentModel.refresh(app, true);

      // Verdict details
      verdictModel.refresh(app, applicationDetails.authorities);

      // Map
      mapModel.refresh(app);

      // Operations
      applicationModel.operationsCount(_.map(_.countBy(app.operations, "name"), function(v, k) { return {name: k, count: v}; }));

      attachmentsTab.refresh(applicationModel);

      // Statements
      requestForStatementModel.setApplicationId(app.id);

      // authorities
      initAuthoritiesSelectList(applicationDetails.authorities);

      // permit subtypes
      permitSubtypes(applicationDetails.permitSubtypes);


      // Mark-seen
      if (applicationModel.infoRequest() && authorizationModel.ok("mark-seen")) {
        ajax.command("mark-seen", {id: app.id, type: "comments"}).call();
      }

      // Documents
      var nonpartyDocs = _.filter(app.documents, util.isNotPartyDoc);
      var sortedNonpartyDocs = _.sortBy(nonpartyDocs, util.getDocumentOrder);
      var partyDocs = _.filter(app.documents, util.isPartyDoc);
      var sortedPartyDocs = _.sortBy(partyDocs, util.getDocumentOrder);
      var allDocs = sortedNonpartyDocs.concat(sortedPartyDocs);

      var nonpartyDocErrors = _.map(sortedNonpartyDocs, function(doc) { return doc.validationErrors; });
      var partyDocErrors = _.map(sortedPartyDocs, function(doc) { return doc.validationErrors; });

      applicationModel.initValidationErrors(nonpartyDocErrors.concat(partyDocErrors));

      docgen.displayDocuments("#applicationDocgen", app, sortedNonpartyDocs, authorizationModel);
      docgen.displayDocuments("#partiesDocgen",     app, sortedPartyDocs, authorizationModel);
      docgen.displayDocuments("#applicationAndPartiesDocgen", app, allDocs, authorizationModel, {accordionCollapsed: true});

      // Indicators
      function sumDocIndicators(sum, doc) {
        return sum + app.documentModificationsPerDoc[doc.id];
      }
      applicationModel.nonpartyDocumentIndicator(_.reduce(nonpartyDocs, sumDocIndicators, 0));
      applicationModel.partyDocumentIndicator(_.reduce(partyDocs, sumDocIndicators, 0));

      // Set the value behind assignee selection list
      var assignee = resolveApplicationAssignee(app.authority);
      var assigneeId = assignee ? assignee.id : null;
      applicationModel.assignee(assigneeId);

      isInitializing = false;
      pageutil.hideAjaxWait();

    });
  }

  hub.subscribe({type: "dialog-close", id: "dialog-valtuutus"}, function() {
    inviteModel.reset();
  });

  // tabs
  var selectedTab = "";
  var tabFlow = false;
  hub.subscribe("set-debug-tab-flow", function(e) {
    tabFlow = e.value;
    $(".tab-content").show(0,function() { selectTab(selectedTab); });
  });

  function openTab(id) {
    // old conversation tab opens both info tab and side panel
    if (id === 'conversation') {
      id = 'info';
      if (!$("#conversation-panel").is(":visible")) {
        $("#open-conversation-side-panel").click();
      }
    }
    if(tabFlow) {
      $('html, body').animate({ scrollTop: $("#application-"+id+"-tab").offset().top}, 100);
    } else {
      $(".tab-content").hide();
      $("#application-"+id+"-tab").fadeIn();
    }
  }

  function markTabActive(id) {
    $("#applicationTabs li").removeClass("active");
    $("a[data-target='"+id+"']").parent().addClass("active");
  }

  function selectTab(tab) {
    markTabActive(tab);
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
          .call();
      }}, 1000);
  }



  function initPage(kind, e) {
    var newId = e.pagePath[0];
    var tab = e.pagePath[1];
    if (newId !== currentId || !tab) {
      pageutil.showAjaxWait();
      currentId = newId;
      mapModel.updateMapSize(kind);
      repository.load(currentId);
    }
    selectTab(tab || "info");
  }

  hub.onPageChange("application", _.partial(initPage, "application"));
  hub.onPageChange("inforequest", _.partial(initPage, "inforequest"));

  repository.loaded(["application","inforequest","attachment","statement","neighbors","task","verdict"], function(application, applicationDetails) {
    if (!currentId || (currentId === application.id)) {
      showApplication(applicationDetails);
    }
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
      window.location.hash = "!/neighbors/" + application.id();
      return false;
    },
    markDone: function(neighbor) {
      ajax
        .command("neighbor-mark-done", {id: currentId, neighborId: neighbor.id()})
        .complete(_.partial(repository.load, currentId, util.nop))
        .call();
    },
    statusCompleted: function(neighbor) {
      return _.contains(["mark-done", "response-given-ok", "response-given-comments"], _.last(neighbor.status()).state());
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

  $(function() {
    var bindings = {
      // function to access accordion
      accordian: accordian,
      // observables
      application: applicationModel,
      authorities: authorities,
      permitSubtypes: permitSubtypes,
      // models
      addLinkPermitModel: addLinkPermitModel,
      addPartyModel: addPartyModel,
      authorization: authorizationModel,
      changeLocationModel: changeLocationModel,
      applicationComment: commentModel,
      constructionStateChangeModel: constructionStateChangeModel,
      createTask: createTaskController,
      invite: inviteModel,
      map: mapModel,
      neighbor: neighborActions,
      neighborStatusModel: neighborStatusModel,
      requestForStatementModel: requestForStatementModel,
      sendNeighborEmailModel: sendNeighborEmailModel,
      stampModel: stampModel,
      signingModel: signingModel,
      verdictModel: verdictModel,
      openInviteCompany: inviteCompanyModel.open.bind(inviteCompanyModel),
      attachmentsTab: attachmentsTab
    };

    $("#application").applyBindings(bindings);
    $("#inforequest").applyBindings(bindings);
    $(changeLocationModel.dialogSelector).applyBindings({changeLocationModel: changeLocationModel});
    $(addLinkPermitModel.dialogSelector).applyBindings({addLinkPermitModel: addLinkPermitModel});
    $(constructionStateChangeModel.dialogSelector).applyBindings({constructionStateChangeModel: constructionStateChangeModel});
    $(signingModel.dialogSelector).applyBindings({signingModel: signingModel, authorization: authorizationModel});
    $(inviteCompanyModel.selector).applyBindings(inviteCompanyModel);
    attachmentsTab.attachmentTemplatesModel.init();
  });

})();
