;(function() {
  "use strict";

  var isInitializing = true;
  var currentId = null;
  var application = new LUPAPISTE.ApplicationModel();
  var authorizationModel = authorization.create();
  var commentModel = comments.create(true);
  var applicationMap = null;
  var inforequestMap = null;
  var changeLocationModel = new LUPAPISTE.ChangeLocationModel();
  var addLinkPermitModel = new LUPAPISTE.AddLinkPermitModel();
  var constructionStateChangeModel = new LUPAPISTE.ModalDatepickerModel();
  constructionStateChangeModel.openConstructionStartDialog = _.partial(
      constructionStateChangeModel.openWithConfig,
      {commandName : "inform-construction-started",
       dateParameter: "startedTimestampStr",
       dateSelectorLabel   : "constructionStarted.startedDate",
       dialogHeader        : "constructionStarted.dialog.header",
       dialogHelpParagraph : "constructionStarted.dialog.helpParagraph",
       dialogButtonSend    : "constructionStarted.dialog.continue",
       areYouSureMessage   : "constructionStarted.dialog.areyousure.message"});
  constructionStateChangeModel.openConstructionReadyDialog = _.partial(
      constructionStateChangeModel.openWithConfig,
      {commandName : "inform-construction-ready",
       dateParameter: "readyTimestampStr",
       extraParameters: {lang: loc.getCurrentLanguage()},
       dateSelectorLabel   : "constructionReady.readyDate",
       dialogHeader        : "constructionReady.dialog.header",
       dialogHelpParagraph : "constructionReady.dialog.helpParagraph",
       dialogButtonSend    : "constructionReady.dialog.continue",
       areYouSureMessage   : "constructionReady.dialog.areyousure.message"});
  constructionStateChangeModel.openBuildingConstructionStartDialog = function(building) {
    console.log(ko.mapping.toJS(building));
    constructionStateChangeModel.openWithConfig({commandName : "inform-building-construction-started",
       dateParameter: "startedDate",
       extraParameters: {buildingIndex: building.index(), lang: loc.getCurrentLanguage()},
       // TODO actual message keys
       dateSelectorLabel   : "constructionStarted.startedDate",
       dialogHeader        : "constructionStarted.dialog.header",
       dialogHelpParagraph : "constructionStarted.dialog.helpParagraph",
       dialogButtonSend    : "constructionStarted.dialog.continue",
       areYouSureMessage   : "constructionStarted.dialog.areyousure.message"}, application);
    return false;
  };

  var inviteModel = new LUPAPISTE.InviteModel();
  var verdictModel = new LUPAPISTE.VerdictsModel();
  var stampModel = new LUPAPISTE.StampModel();
  var requestForStatementModel = new LUPAPISTE.RequestForStatementModel();
  var addPartyModel = new LUPAPISTE.AddPartyModel();
  var createTaskModel = new LUPAPISTE.CreateTaskModel();

  var authorities = ko.observableArray([]);
  var permitSubtypes = ko.observableArray([]);
  var attachmentsByGroup = ko.observableArray();

  function getAttachmentsByGroup(source) {
    var attachments = _.map(source, function(a) {
      a.latestVersion = _.last(a.versions || []);
      a.statusName = LUPAPISTE.statuses[a.state] || "unknown";
      return a;
    });
    var grouped = _.groupBy(attachments, function(attachment) { return attachment.type['type-group']; });
    return _.map(grouped, function(attachments, group) { return {group: group, attachments: attachments}; });
  }

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

  application.assignee.subscribe(function(v) { updateAssignee(v); });
  application.permitSubtype.subscribe(function(v){updatePermitSubtype(v);});

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

  // When Oskari map has initialized itself, draw shapes and marker
  hub.subscribe("oskari-map-initialized", function() {

    if (application.drawings && application.drawings().length) {
      var drawings = _.map(application.drawings(), function(d) {
        return {
          "id": d.id(),
          "name": d.name? d.name() :"",
          "desc": d.desc ? d.desc() : "",
          "category": d.category ? d.category() : "",
          "geometry": d.geometry ? d.geometry() : "",
          "area": d.area? d.area() : "",
          "height": d.height? d.height():""
        }});

      hub.send("oskari-show-shapes", {
        drawings: drawings,
        style: {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF"},
        clear: true
      });
    }

    var x = (application.location && application.location().x) ? application.location().x() : 0;
    var y = (application.location && application.location().y) ? application.location().y() : 0;
    hub.send("oskari-center-map", {
      data:  [{location: {x: x, y: y}, iconUrl: "/img/map-marker.png"}],
      clear: true
    });
  });

  // When a shape is draw in Oskari map, save it to application
  hub.subscribe("oskari-save-drawings", function(e) {
    ajax.command("save-application-drawings", {id: currentId, drawings: e.data.drawings})
    .success(function() {
      repository.load(currentId);
    })
    .call();
  });

  function showApplication(applicationDetails) {
    isInitializing = true;

    authorizationModel.refreshWithCallback({id: applicationDetails.application.id}, function() {
      var app = applicationDetails.application;

      // Delete shapes
      if (application.shapes) {
        delete application.shapes;
      }

      // Plain data
      application._js = app;

      // Update observebles
      ko.mapping.fromJS(app, {}, application);

      // Invite
      inviteModel.setApplicationId(app.id);

      // Comments:
      commentModel.refresh(app);

      // Verdict details
      verdictModel.refresh(app);

      // Operations:

      application.operationsCount(_.map(_.countBy(app.operations, "name"), function(v, k) { return {name: k, count: v}; }));

      // Attachments:
      attachmentsByGroup(getAttachmentsByGroup(app.attachments));

      // Setting disable value for the "Send unsent attachments" button:

      var unsentAttachmentFound =
        _.some(app.attachments, function(a) {
          var lastVersion = _.last(a.versions);
          return lastVersion &&
                 (!a.sent || lastVersion.created > a.sent) &&
                 (!a.target || (a.target.type !== "statement" && a.target.type !== "verdict"));
        });
      application.unsentAttachmentsNotFound(!unsentAttachmentFound);

      // Statements
      requestForStatementModel.setApplicationId(app.id);

      // authorities
      initAuthoritiesSelectList(applicationDetails.authorities);

      // permit subtypes
      permitSubtypes(applicationDetails.permitSubtypes);

      // Update map:
      var location = application.location();
      var x = location.x();
      var y = location.y();

      if(x === 0 && y === 0) {
        $('#application-map').css("display", "none");
      } else {
        $('#application-map').css("display", "inline-block");
      }

      var map = getOrCreateMap(application.infoRequest() ? "inforequest" : "application");
      map.clear().center(x, y, 10).add(x, y);

      if (application.shapes && application.shapes().length > 0) {
        map.drawShape(application.shapes()[0]);
      }

      if (application.infoRequest() && authorizationModel.ok("mark-seen")) {
        ajax.command("mark-seen", {id: app.id, type: "comments"}).call();
      }

      // Documents
      var nonpartyDocs = _.filter(app.documents, function(doc) {return doc.schema.info.type !== "party"; });
      var partyDocs = _.filter(app.documents, function(doc) {return doc.schema.info.type === "party"; });
      docgen.displayDocuments("#applicationDocgen", app, nonpartyDocs, authorizationModel);
      docgen.displayDocuments("#partiesDocgen",     app, partyDocs, authorizationModel);

      function sumDocIndicators(sum, doc) {
        return sum + app.documentModificationsPerDoc[doc.id];
      }
      application.nonpartyDocumentIndicator(_.reduce(nonpartyDocs, sumDocIndicators, 0));
      application.partyDocumentIndicator(_.reduce(partyDocs, sumDocIndicators, 0));

      // set the value behind assignee selection list
      var assignee = resolveApplicationAssignee(app.authority);
      var assigneeId = assignee ? assignee.id : null;
      application.assignee(assigneeId);

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
      var tabMeta = {"conversation": {type: "comments",   model: application.unseenComments},
                      "statement":   {type: "statements", model: application.unseenStatements},
                      "verdict":     {type: "verdicts",   model: application.unseenVerdicts}};
      // Mark comments seen after a second
      if (tabMeta[tab] && currentId && authorizationModel.ok("mark-seen")) {
        ajax.command("mark-seen", {id: currentId, type: tabMeta[tab].type})
          .success(function() {tabMeta[tab].model(0);})
          .call();
      }}, 1000);
  }

  var accordian = function(data, event) { accordion.toggle(event); };

  var attachmentTemplatesModel = new function() {
    var self = this;

    self.ok = function(ids) {
      ajax.command("create-attachments", {id: application.id(), attachmentTypes: ids})
        .success(function() { repository.load(application.id()); })
        .complete(LUPAPISTE.ModalDialog.close)
        .call();
    };

    self.init = function() {
      self.selectm = $("#dialog-add-attachment-templates .attachment-templates").selectm();
      self.selectm.ok(self.ok).cancel(LUPAPISTE.ModalDialog.close);
      return self;
    };

    self.show = function() {
      var data = _.map(application.allowedAttachmentTypes(), function(g) {
        var groupId = g[0];
        var groupText = loc("attachmentType." + groupId + "._group_label");
        var attachemntIds = g[1];
        var attachments = _.map(attachemntIds, function(a) {
          var id = {"type-group": groupId, "type-id": a};
          var text = loc("attachmentType." + groupId + "." + a);
          return {id: id, text: text};
        });
        return [groupText, attachments];
      });
      self.selectm.reset(data);
      LUPAPISTE.ModalDialog.open("#dialog-add-attachment-templates");
      return self;
    };
  }();

  function createMap(divName) { return gis.makeMap(divName, false).center([{x: 404168, y: 6693765}], 12); }

  function getOrCreateMap(kind) {
    if (kind === "application") {
      if (!applicationMap) applicationMap = createMap("application-map");
      return applicationMap;
    } else if (kind === "inforequest") {
      if (!inforequestMap) inforequestMap = createMap("inforequest-map");
      return inforequestMap;
    } else {
      throw "Unknown kind: " + kind;
    }
  }

  function initPage(kind, e) {
    var newId = e.pagePath[0];
    var tab = e.pagePath[1];
    if (newId !== currentId || !tab) {
      pageutil.showAjaxWait();
      currentId = newId;
      getOrCreateMap(kind).updateSize();
      repository.load(currentId);
    }
    selectTab(tab || "info");
  }

  hub.onPageChange("application", _.partial(initPage, "application"));
  hub.onPageChange("inforequest", _.partial(initPage, "inforequest"));

  repository.loaded(["application","inforequest","attachment","statement","neighbors","task"], function(application, applicationDetails) {
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
      var l = neighbor.lastStatus;
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
        .command("neighbor-mark-done", {id: currentId, neighborId: neighbor.neighborId()})
        .complete(_.partial(repository.load, currentId, util.nop))
        .call();
    },
    statusCompleted: function(neighbor) {
      return _.contains(["mark-done", "response-given-ok", "response-given-comments"], neighbor.lastStatus.state());
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
        .id(application.id())
        .neighborId(neighbor.neighborId())
        .propertyId(neighbor.neighbor.propertyId())
        .name(neighbor.neighbor.owner.name())
        .email(neighbor.neighbor.owner.email());
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
      application: application,
      authorities: authorities,
      permitSubtypes: permitSubtypes,
      attachmentsByGroup: attachmentsByGroup,
      comment: commentModel,
      invite: inviteModel,
      authorization: authorizationModel,
      accordian: accordian,
      addPartyModel: addPartyModel,
      attachmentTemplatesModel: attachmentTemplatesModel,
      requestForStatementModel: requestForStatementModel,
      verdictModel: verdictModel,
      stampModel: stampModel,
      changeLocationModel: changeLocationModel,
      neighbor: neighborActions,
      sendNeighborEmailModel: sendNeighborEmailModel,
      neighborStatusModel: neighborStatusModel,
      addLinkPermitModel: addLinkPermitModel,
      constructionStateChangeModel: constructionStateChangeModel,
      createTaskModel: createTaskModel
    };

    $("#application").applyBindings(bindings);
    $("#inforequest").applyBindings(bindings);
    $(changeLocationModel.dialogSelector).applyBindings({changeLocationModel: changeLocationModel});
    $(addLinkPermitModel.dialogSelector).applyBindings({addLinkPermitModel: addLinkPermitModel});
    $(constructionStateChangeModel.dialogSelector).applyBindings({constructionStateChangeModel: constructionStateChangeModel});
    $(createTaskModel.dialogSelector).applyBindings({createTaskModel: createTaskModel});
    attachmentTemplatesModel.init();
  });

})();
