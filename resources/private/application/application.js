;(function() {
  "use strict";

  var isInitializing = true;
  var currentId = null;
  var authorizationModel = authorization.create();
  var commentModel = comments.create(true);
  var applicationMap = null;
  var inforequestMap = null;
  var changeLocationModel = new LUPAPISTE.ChangeLocationModel();
  var addLinkPermitModel = new LUPAPISTE.AddLinkPermitModel();
  var inviteModel = new LUPAPISTE.InviteModel();
  var verdictModel = new LUPAPISTE.VerdictsModel();

  function isNum(s) {
    return s && s.match(/^\s*\d+\s*$/) !== null;
  }

  var transparencies = _.map([0,25,50,75,100], function(v) {
    return {text: loc(["stamp.transparency", v.toString()]), value: Math.round(255 * v / 100.0)};
  });

  var stampModel = new function() {
    var self = this;

                               // Start:  Cancel:  Ok:
    self.statusInit      = 0;  //   -       -       -
    self.statusReady     = 1;  //   +       +       -
    self.statusStarting  = 2;  //   -       -       -
    self.statusRunning   = 3;  //   -       -       -
    self.statusDone      = 4;  //   -       -       +
    self.statusNoFiles   = 5;  //   -       -       +

    self.status = ko.observable(self.statusStarting);
    self.application = null;
    self.applicationId = null;
    self.files = ko.observable(null);
    self.selectedFiles = ko.computed(function() { return _.filter(self.files(), function(f) { return f.selected(); }); });
    self.jobId = null;
    self.jobVersion = null;

    self.xMargin = ko.observable("");
    self.xMarginOk = ko.computed(function() { return isNum(self.xMargin()); });
    self.yMargin = ko.observable("");
    self.yMarginOk = ko.computed(function() { return isNum(self.yMargin()); });
    self.transparency = ko.observable();
    self.transparencies = transparencies;

    function stampableAttachment(a) {
      var ct = "";
      if (a.latestVersion && typeof a.latestVersion.contentType === "function") {
        ct = a.latestVersion.contentType();
      }
      return ct === "application/pdf" || ct.search(/^image\//) === 0;
    }

    function normalizeAttachment(a) {
      var versions = _(a.versions()).reverse().value(),
          restamp = (typeof versions[0].stamped === "function" && versions[0].stamped()),
          selected = restamp ? versions[1] : versions[0];
      return {
        id:           a.id(),
        type:         { "type-group": a.type["type-group"](), "type-id": a.type["type-id"]() },
        contentType:  selected.contentType(),
        filename:     selected.filename(),
        version:      { major: selected.version.major(), minor: selected.version.minor() },
        size:         selected.size(),
        selected:     ko.observable(true),
        status:       ko.observable(""),
        restamp:      restamp
      };
    }

    self.init = function(application) {
      self.application = application;

      self
        .files(_(application.attachments()).filter(stampableAttachment).map(normalizeAttachment).value())
        .status(self.files().length > 0 ? self.statusReady : self.statusNoFiles)
        .xMargin("10")
        .yMargin("85")
        .transparency(self.transparencies[0]);

      LUPAPISTE.ModalDialog.open("#dialog-stamp-attachments");
      return self;
    };

    self.start = function() {
      self.status(self.statusStarting);
      ajax
        .command("stamp-attachments", {
          id: self.application.id(),
          files: _.map(self.selectedFiles(), "id"),
          xMargin: _.parseInt(self.xMargin(), 10),
          yMargin: _.parseInt(self.yMargin(), 10),
          transparency: self.transparency().value
        })
        .success(self.started)
        .call();
      return false;
    };

    self.started = function(data) {
      self.jobId = data.job.id;
      self.jobVersion = 0;
      self.status(self.statusRunning).queryUpdate();
      return false;
    };

    self.queryUpdate = function() {
      ajax
        .query("stamp-attachments-job")
        .param("job-id", self.jobId)
        .param("version", self.jobVersion)
        .success(self.update)
        .call();
      return self;
    };

    self.update = function(data) {
      if (data.result === "update") {
        var update = data.job;

        self.jobVersion = update.version;
        _.each(update.value, function (newStatus, fileId) {
          _(self.files()).filter({id: fileId}).each(function(f) { f.status(newStatus); });
        });

        if (update.status === "done") {
          repository.load(self.application.id());
          return self.status(self.statusDone);
        }
      }

      return self.queryUpdate();
    };

    function selectAllFiles(value) { _.each(self.files(), function(f) { f.selected(value); }); }
    self.selectAll = _.partial(selectAllFiles, true);
    self.selectNone = _.partial(selectAllFiles, false);

  }();

  var removeApplicationModel = new function() {
    var self = this;

    self.applicationId = null;

    self.init = function(applicationId) {
      self.applicationId = applicationId;
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("areyousure"),
        loc("areyousure.message"),
        {title: loc("yes"), fn: self.ok},
        {title: loc("no")}
      );
      return self;
    };
    self.ok = function() {
      ajax
        .command("cancel-application", {id: self.applicationId})
        .success(function() {
          window.location.hash = "!/applications";
        })
        .call();
      return false;
    };
  }();

  var removeAuthModel = new function() {
    var self = this;

    self.applicationId = null;
    self.username = null;

    self.init = function(applicationId, username) {
      self.applicationId = applicationId;
      self.username = username;
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("areyousure"),
        loc("areyousure.message"),
        {title: loc("yes"), fn: self.ok},
        {title: loc("no")}
      );
      return self;
    };

    self.ok = function() {
      ajax.command("remove-auth", { id : self.applicationId, email : self.username})
        .success(function() {
          notify.success("oikeus poistettu", self.username);
          repository.load(self.applicationId);
        })
        .call();
      return false;
    };
  }();

  var requestForStatementModel = new function() {
    var self = this;
    self.data = ko.observableArray();
    self.personIds = ko.observableArray([]);
    self.submitting = ko.observable(false);

    self.disabled = ko.computed(function() {
      return _.isEmpty(self.personIds()) || self.submitting();
    });

    self.load = function() {
      ajax
        .query("get-statement-persons", {id: currentId})
        .success(function(result) { self.data(ko.mapping.fromJS(result.data)); })
        .call();
    };

    self.openDialog = function() {
      self.load();
      LUPAPISTE.ModalDialog.open("#dialog-request-for-statement");
    };

    self.send = function() {
      self.submitting(true);
      ajax.command("request-for-statement", {id: currentId, personIds: self.personIds()})
        .success(function() {
          self.personIds([]);
          repository.load(currentId);
          LUPAPISTE.ModalDialog.close();
        })
        .complete(function() { self.submitting(false); })
        .call();
    };

    self.openStatement = function(model) {
      window.location.hash = "#!/statement/" + currentId + "/" + model.id();
      return false;
    };

  }();

  var submitApplicationModel = new function() {
    var self = this;

    self.applicationId = null;

    self.init = function(applicationId) {
      self.applicationId = applicationId;
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("application.submit.areyousure.title"),
        loc("application.submit.areyousure.message"),
        {title: loc("yes"), fn: self.ok},
        {title: loc("no")}
      );
      return self;
    };

    self.ok = function() {
      ajax.command("submit-application", {id: self.applicationId})
        .success(function() { repository.load(self.applicationId); })
        .call();
      return false;
    };
  }();

  var addPartyModel = new function() {
    var self = this;

    self.applicationId = null;
    self.partyDocumentNames = ko.observableArray();

    self.documentName = ko.observable();

    self.init = function(applicationId) {
      self.applicationId = applicationId;
      ajax.query("party-document-names", {id: applicationId}).success(function(d) { self.partyDocumentNames(ko.mapping.fromJS(d.partyDocumentNames));}).call();

      LUPAPISTE.ModalDialog.open("#dialog-add-party");
      return false;
    };

    self.addPartyEnabled = function() {
      return self.documentName();
    };

    self.addParty = function () {
      ajax.command("create-doc", {id: self.applicationId, schemaName: self.documentName()})
        .success(function() { repository.load(self.applicationId); })
        .call();
      return false;
    };
  }();

  function ApplicationModel() {
    var self = this;
    self.id = ko.observable();
    self.infoRequest = ko.observable();
    self.openInfoRequest = ko.observable();
    self.state = ko.observable();
    self.location = ko.observable();
    self.municipality = ko.observable();
    self.permitType = ko.observable();
    self.propertyId = ko.observable();
    self.title = ko.observable();
    self.created = ko.observable();
    self.documents = ko.observable();
    self.attachments = ko.observableArray();
    self.hasAttachment = ko.observable(false);
    self.address = ko.observable();
    self.operations = ko.observable();
    self.permitSubtype = ko.observable();
    self.operationsCount = ko.observable();
    self.applicant = ko.observable();
    self.assignee = ko.observable();
    self.neighbors = ko.observable();
    self.nonpartyDocumentIndicator = ko.observable(0);
    self.partyDocumentIndicator = ko.observable(0);
    self.linkPermitData = ko.observable({});
    self.appsLinkingToUs = ko.observable({});
    self.unsentAttachmentsNotFound = ko.observable(false);
    self.pending = ko.observable(false);
    self.processing = ko.observable(false);
    self.sendUnsentAttachmentsButtonDisabled = ko.computed(function() {
      return self.pending() || self.processing() || self.unsentAttachmentsNotFound();
    });

    self.attachmentsRequiringAction = ko.observable();
    self.unseenStatements = ko.observable();
    self.unseenVerdicts = ko.observable();
    self.unseenComments = ko.observable();
        // new stuff
    self.invites = ko.observableArray();

    // all data in here
    self.data = ko.observable();

    self.roles = ko.computed(function() {
      var value = [];

      if (self.data() !== undefined) {
        var auth = ko.utils.unwrapObservable(self.data().auth());
        var withRoles = function(r, i) {
          var a = r[i.id()] || (i.roles = [], i);
          a.roles.push(i.role());
          r[i.id()] = a;
          return r;
        };
        var pimped = _.reduce(auth, withRoles, {});
        value = _.values(pimped);
      }
      return value;
    });

    self.openOskariMap = function() {
      var coords = "&coord=" + self.location().x() + "_" + self.location().y();
      var zoom = "&zoomLevel=12";
      var features = "&addPoint=1&addArea=1";
      var lang = "&lang=" + loc.getCurrentLanguage();
      var url = '/oskari/fullmap.html?id=' + self.id() + coords + zoom + features + lang;
      window.open(url);
    };

    self.submitApplication = function() {
      submitApplicationModel.init(self.id());
      return false;
    };

    self.requestForComplement = function(model) {
      var applicationId = self.id();
      ajax.command("request-for-complement", { id: applicationId})
        .success(function() {
          notify.success("pyynt\u00F6 l\u00E4hetetty",model);
          repository.load(applicationId);
        })
        .call();
      return false;
    };

    self.convertToApplication = function() {
      var id = self.id();
      ajax.command("convert-to-application", {id: id})
        .success(function() {
          repository.load(id);
          window.location.hash = "!/application/" + id;
        })
        .call();
      return false;
    };

    self.approveApplication = function(model) {
      var applicationId = self.id();
      ajax.command("approve-application", {id: applicationId, lang: loc.getCurrentLanguage()})
        .success(function() {
        //FIXME parempi tapa ilmoittaa onnistumisesta
          notify.success("hakemus hyv\u00E4ksytty",model);
          repository.load(applicationId);
        })//FIXME parempi/tyylikaampi virheilmoitus
        .error(function(resp) {alert(resp.text);})
        .call();
      return false;
    };

    self.refreshKTJ = function(model) {
      var applicationId = self.id();
      ajax.command("refresh-ktj", { id: applicationId})
        .success(function() {
          //FIXME parempi tapa ilmoittaa onnistumisesta
          notify.success("KTJ tiedot p\u00e4ivitetty",model);
          repository.load(applicationId);
        })//FIXME parempi/tyylikaampi virheilmoitus
        .error(function(resp) {alert(resp.text);})
        .call();
      return false;
    };

    self.removeInvite = function(model) {
      var applicationId = self.id();
      ajax.command("remove-invite", { id : applicationId, email : model.user.username()})
        .success(function() {
          notify.success("kutsu poistettu", model);
          repository.load(applicationId);
        })
        .call();
      return false;
    };

    self.removeAuth = function(model) {
      removeAuthModel.init(self.id(), model.username());
      return false;
    };

    self.isNotOwner = function(model) {
      return model.role() !== "owner";
    };

    self.addOperation = function() {
      window.location.hash = "#!/add-operation/" + self.id();
      return false;
    };

    self.addParty = function() {
      addPartyModel.init(self.id());
      return false;
    };

    self.cancelApplication = function() {
      removeApplicationModel.init(self.id());
      return false;
    };

    self.exportPdf = function() {
      window.open("/api/raw/pdf-export?id=" + self.id() + "&lang=" + loc.currentLanguage, "_blank");
      return false;
    };

    self.stampAttachments = function() {
      stampModel.init(self);
      return false;
    };

    self.newAttachment = function() {
      attachment.initFileUpload(currentId, null, null, true);
    };

    self.newOtherAttachment = function() {
      attachment.initFileUpload(currentId, null, 'muut.muu', false);
    };

    // TODO: Tarvittaessa liitteiden lahetykselle voi tehda confirmation modaalin,
    //       kts. esim attachment.js:n "deleteAttachment"
    self.sendUnsentAttachmentsToBackingSystem = function() {
      var appId = self.id();
      ajax
      .command("move-attachments-to-backing-system", {id: appId, lang: loc.getCurrentLanguage()})
      .success(function(data) {
        repository.load(appId);
      })
      .processing(self.processing)
      .pending(self.pending)
    .call();
    };



    self.createChangePermit = function() {

// Create.js:sta mallia:
//
//      ajax.command("create-application", {
//        infoRequest: infoRequest,
//        operation: self.operation(),
//        y: self.y(),
//        x: self.x(),
//        address: self.addressString(),
//        propertyId: util.prop.toDbFormat(self.propertyId()),
//        messages: isBlank(self.message()) ? [] : [self.message()],
//        municipality: self.municipality().id
//      })
//      .processing(self.processing)
//      .pending(self.pending)
//      .success(function(data) {
//        setTimeout(self.clear, 0);
//        window.location.hash = (infoRequest ? "!/inforequest/" : "!/application/") + data.id;
//      })
//      .call();

      var appId = self.id();
        ajax
        .command("create-change-permit", {id: appId/*, lang: loc.getCurrentLanguage()*/})
        .success(function(data) {
          repository.load(data.id);
          window.location.hash = "#!/application/" + data.id;
        })
      .call();
    };


    self.changeTab = function(model,event) {
      var $target = $(event.target);
      while ($target.is("span")) {
        $target = $target.parent();
      }
      var targetTab = $target.attr("data-target");
      window.location.hash = "#!/application/" + self.id() + "/" + targetTab;
    };
     self.nextTab = function(model,event) {
      var $target = $(event.target);
      while ($target.is("span")) {
        $target = $target.parent();
      }
      var targetTab = $target.attr("data-target");
      window.location.hash = "#!/application/" + self.id() + "/" + targetTab;
      var y = $('#applicationTabs').position().top + 40;
      window.scrollTo(0,y);
    };
  }

  var application = new ApplicationModel();

  var authorities = ko.observableArray([]);
  var permitSubtypes = ko.observableArray([]);
  var attachments = ko.observableArray([]);
  var attachmentsByGroup = ko.observableArray();

  function getAttachmentsByGroup(source) {
    var attachments = _.map(source, function(a) { a.latestVersion = _.last(a.versions || []); return a; });
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

  function initPermitSubtypesSelectList(data){
      permitSubtypes(data);
  }

  // When Oskari map has initialized itself, draw shapes and marker
  hub.subscribe("oskari-map-initialized", function() {

    if (application.drawings && application.drawings().length) {
      var drawings = _.map(application.drawings(), function(d) {
        return {
          "id": d.id(),
          "name": d.name(),
          "desc": d.desc(),
          "category": d.category(),
          "geometry": d.geometry()
        }});

      hub.send("oskari-show-shapes", {
        drawings: drawings,
        style: {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF"},
        clear: true
      });
    }

    var x = (application.location && application.location().x) ? application.location().x() : 0;
    var y = (application.location && application.location().y) ? application.location().y() : 0;
    hub.send("documents-map", {
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
      var documents = app.documents;

      // Performance improvement: documents should not be mapped with ko.mapping
      delete app.documents;

      // Delete shapes
      if(application.shapes) {
        delete application.shapes;
      }

      application.data(ko.mapping.fromJS(app));
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

      var statuses = {
        requires_user_action: "missing",
        requires_authority_action: "new",
        ok: "ok"
      };

      application.hasAttachment(false);

      attachments(_.map(app.attachments || [], function(a) {
        a.statusName = statuses[a.state] || "unknown";
        a.latestVersion = _.last(a.versions);
        if (a.versions && a.versions.length) { application.hasAttachment(true); }
        return a;
      }));

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


      // authorities
      initAuthoritiesSelectList(applicationDetails.authorities);

      // permit subtypes
      initPermitSubtypesSelectList(applicationDetails.permitSubtypes);

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
      var nonpartyDocs = _.filter(documents, function(doc) {return doc.schema.info.type !== "party"; });
      var partyDocs = _.filter(documents, function(doc) {return doc.schema.info.type === "party"; });
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

  repository.loaded(["application","inforequest","attachment","statement","neighbors"], function(application, applicationDetails) {
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
      attachments: attachments,
      attachmentsByGroup: attachmentsByGroup,
      comment: commentModel,
      invite: inviteModel,
      authorization: authorizationModel,
      accordian: accordian,
      addPartyModel: addPartyModel,
      removeApplicationModel: removeApplicationModel,
      attachmentTemplatesModel: attachmentTemplatesModel,
      requestForStatementModel: requestForStatementModel,
      verdictModel: verdictModel,
      stampModel: stampModel,
      changeLocationModel: changeLocationModel,
      neighbor: neighborActions,
      sendNeighborEmailModel: sendNeighborEmailModel,
      neighborStatusModel: neighborStatusModel,
      addLinkPermitModel: addLinkPermitModel
    };

    $("#application").applyBindings(bindings);
    $("#inforequest").applyBindings(bindings);
    $("#dialog-change-location").applyBindings({changeLocationModel: changeLocationModel});
    $("#dialog-add-link-permit").applyBindings({addLinkPermitModel: addLinkPermitModel});
    attachmentTemplatesModel.init();
  });

})();
