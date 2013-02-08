;(function() {
  "use strict";

  var isInitializing = true;
  var currentId;
  var authorizationModel = authorization.create();
  var commentModel = comments.create();
  var applicationMap;
  var inforequestMap;

  var removeDocModel = new function() {
    var self = this;

    self.appId = ko.observable();
    self.docId = ko.observable();
    self.docName = ko.observable();
    self.callback = null;

    self.init = function(appId, docId, docName, callback) {
      self.appId(appId).docId(docId).docName(docName);
      self.callback = callback;
      LUPAPISTE.ModalDialog.open("#dialog-remove-doc");
      return self;
    };

    self.ok = function() {
      ajax
        .command("remove-doc", {id: self.appId(), docId: self.docId()})
        .success(self.callback)
        .call();
      return false;
    };

    self.cancel = function() { return true; };

  }();

  var applicationModel = new function() {
    var self = this;

    self.data = ko.observable();

    self.auth = ko.computed(function() {
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
    }, self);
  }();

  var removeApplicationModel = new function() {
    var self = this;

    self.applicationId = ko.observable();

    self.init = function(applicationId) {
      self.applicationId(applicationId);
      LUPAPISTE.ModalDialog.open("#dialog-confirm");
      return self;
    };

    self.ok = function() {
      ajax
        .command("cancel-application", {id: this.applicationId()})
        .success(function() {
          window.location.hash = "!/applications";
        })
        .call();
      return false;
    };

    self.cancel = function() { return true; };
  }();


  var application = {
    id: ko.observable(),
    infoRequest: ko.observable(),
    state: ko.observable(),
    location: ko.observable(),
    permitType: ko.observable(),
    propertyId: ko.observable(),
    title: ko.observable(),
    created: ko.observable(),
    documents: ko.observable(),
    attachments: ko.observableArray(),
    hasAttachment: ko.observable(false),
    address: ko.observable(),
    verdict: ko.observable(),
    operations: ko.observable(),
    applicant: ko.observable(),
    assignee: ko.observable(),

    // new stuff
    invites: ko.observableArray(),

    // all data in here
    data: ko.observable(),

    openOskariMap: function() {
      var url = '/oskari/fullmap.html?coord=' + application.location().x() + '_' + application.location().y() + '&zoomLevel=10';
      window.open(url);
      var applicationId = application.id();

      hub.subscribe("map-initialized", function() {
        if(application.shapes && application.shapes().length > 0) {
          oskariDrawShape(application.shapes()[0]);
        }

        oskariSetMarker(application.location().x(), application.location().y());
      });

      hub.subscribe("map-draw-done", function(e) {
        var drawing = "" + e.data.drawing;
        ajax.command("save-application-shape", {id: applicationId, shape: drawing})
        .success(function() {
          repository.load(applicationId);
        })
        .call();
      });
    },

    submitApplication: function(model) {
      var applicationId = application.id();
      ajax.command("submit-application", { id: applicationId})
        .success(function() {
          notify.success("hakemus j\u00E4tetty",model);
          repository.load(applicationId);
        })
        .call();
      return false;
    },

    markInforequestAnswered: function(model) {
      var applicationId = application.id();
      ajax.command("mark-inforequest-answered", {id: applicationId})
        .success(function() {
          notify.success("neuvontapyynt\u00F6 merkitty vastatuksi",model);
          repository.load(applicationId);
        })
        .call();
      return false;
    },

    convertToApplication: function() {
      var id = application.id();
      ajax.command("convert-to-application", {id: id})
        .success(function() {
          repository.load(id);
          window.location.hash = "!/application/" + id;
        })
        .call();
      return false;
    },

    approveApplication: function(model) {
      var applicationId = application.id();
      ajax.command("approve-application", { id: applicationId})
        .success(function() {
          notify.success("hakemus hyv\u00E4ksytty",model);
          repository.load(applicationId);
        })
        .call();
      return false;
    },

    removeInvite: function(model) {
      var applicationId = application.id();
      ajax.command("remove-invite", { id : applicationId, email : model.user.username()})
        .success(function() {
          notify.success("kutsu poistettu", model);
          repository.load(applicationId);
        })
        .call();
      return false;
    },

    removeAuth: function(model) {
      var applicationId = application.id();
      ajax.command("remove-auth", { id : applicationId, email : model.username()})
        .success(function() {
          notify.success("oikeus poistettu", model);
          repository.load(applicationId);
        })
        .call();
      return false;
    },

    isNotOwner: function(model) {
      return model.role() !== "owner";
    },

    addOperation: function() {
      window.location.hash = "#!/add-operation/" + application.id();
      return false;
    },

    cancelApplication: function() {
      var id = application.id();
      removeApplicationModel.init(id);
      return false;
    }

  };

  var authorities = ko.observableArray([]);
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

  function updateAssignee(value) {
    // do not update assignee if page is still initializing
    if (isInitializing) {
      return;
    }

    // The right is validated in the back-end. This check is just to prevent error.
    if (!authorizationModel.ok('assign-application')) {
      return;
    }

    var assigneeId = value ? value : null;

    ajax.command("assign-application", {id: currentId, assigneeId: assigneeId})
      .success(function() {})
      .error(function(e) { error(e); })
      .fail(function(e) { error(e); })
      .call();
  }

  function oskariDrawShape(shape) {
    hub.send("map-viewvectors", {
      drawing: shape,
      style: {fillColor: "#3CB8EA", fillOpacity: 0.35, strokeColor: "#0000FF"},
      clear: false
    });
  }

  function oskariSetMarker(x, y) {
    hub.send("documents-map",{
      data:  [ {location: {x: x, y: y}} ],
      clear: true
      });
  }

  application.assignee.subscribe(function(v) { updateAssignee(v); });

  function resolveApplicationAssignee(roles) {
    return (roles && roles.authority) ? new AuthorityInfo(roles.authority.id, roles.authority.firstName, roles.authority.lastName) : null;
  }

  function initAuthoritiesSelectList(data) {
    authorities.removeAll();
    _.each(data || [], function(authority) {
      authorities.push(new AuthorityInfo(authority.id, authority.firstName, authority.lastName));
    });
  }

  function showApplication(applicationDetails) {
    isInitializing = true;
    authorizationModel.refresh(applicationDetails.application,function() {

      // new data mapping

      var app = applicationDetails.application;
      applicationModel.data(ko.mapping.fromJS(app));
      ko.mapping.fromJS(app, {}, application);

      // Comments:

      commentModel.setApplicationId(app.id);
      commentModel.setComments(app.comments);

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
        if (a.versions && a.versions.length) application.hasAttachment(true);
        return a;
      }));

      attachmentsByGroup(getAttachmentsByGroup(app.attachments));

      initAuthoritiesSelectList(applicationDetails.authorities);

      // Update map:
      var location = application.location();
      var x = location.x();
      var y = location.y();
      applicationMap.clear().add(x, y).center(x, y, 11);
      inforequestMap.clear().add(x, y).center(x, y, 11);

      if(application.shapes && application.shapes().length > 0) {
        applicationMap.drawShape(application.shapes()[0]);
        inforequestMap.drawShape(application.shapes()[0]);
      }

      // docgen:
      var save = function(path, value, callback, data) {
        ajax
          .command("update-doc", {doc: data.doc, id: data.app, updates: [[path, value]]})
          // Server returns empty array (all ok), or array containing an array with three
          // elements: [key status message]. Here we use just the status.
          .success(function(e) {
            var status = (e.results.length === 0) ? "ok" : e.results[0][1];
            callback(status);
          })
          .error(function(e) { error(e); callback("err"); })
          .fail(function(e) { error(e); callback("err"); })
          .call();
      };

      function displayDocuments(containerSelector, documents) {

        var groupedDocs = _.groupBy(documents, function (doc) { return doc.schema.info.name; });

        var displayOrder = ["hankkeen-kuvaus", "rakennuspaikka", "purku", "uusiRakennus", "lisatiedot", "hakija", "paasuunnittelija", "suunnittelija", "maksaja"];
        var sortedDocs = _.sortBy(groupedDocs, function (docGroup) { return _.indexOf(displayOrder, docGroup[0].schema.info.name); });

        var docgenDiv = $(containerSelector).empty();
        _.each(sortedDocs, function(docGroup) {
          _.each(docGroup, function(doc) {
            docgenDiv.append(new LUPAPISTE.DocModel(doc.schema, doc.body, save, removeDocModel.init, doc.id, application.id()).element);
          });

          var schema = docGroup[0].schema;

          if (schema.info.repeating) {
            var btn = LUPAPISTE.DOMUtils.makeButton(schema.info.name + "_append_btn", loc(schema.info.name + "._append_label"));

            $(btn).click(function() {
              var self = this;
              ajax
                .command("create-doc", {schema: schema.info.name, id: application.id()})
                .success(function(data) {
                  var newDocId = data.doc;
                  var newElem = new LUPAPISTE.DocModel(schema, {}, save, removeDocModel.init, newDocId, application.id()).element;
                  $(self).before(newElem);
                })
                .call();
            });
            docgenDiv.append(btn);
          }
        });
      }

      var partyDocumentNames = ["hakija", "paasuunnittelija", "suunnittelija", "maksaja"];
      displayDocuments("#applicationDocgen", _.filter(app.documents, function(doc) {return !_.contains(partyDocumentNames, doc.schema.info.name);}));
      displayDocuments("#partiesDocgen", _.filter(app.documents, function(doc) {return _.contains(partyDocumentNames, doc.schema.info.name);}));

      if(! isTabSelected('#applicationTabs')) {
        selectDefaultTab('#applicationTabs');
      }

      // set the value behind assignee selection list
      var assignee = resolveApplicationAssignee(app.roles);
      var assigneeId = assignee ? assignee.id : null;
      application.assignee(assigneeId);

      isInitializing = false;
    });
  }

  repository.loaded(function(e) {
    if (!currentId || (currentId === e.applicationDetails.application.id)) {
      showApplication(e.applicationDetails);
    }
  });

  var inviteModel = new function() {
    var self = this;

    self.email = ko.observable();
    self.text = ko.observable();
    self.documentName = ko.observable();
    self.documentId = ko.observable();
    self.error = ko.observable();

    self.submit = function(model) {
      var email = model.email();
      var text = model.text();
      var documentName = model.documentName();
      var documentId = model.documentId();
      var id = application.id();
      ajax.command("invite", { id: id,
                               documentName: documentName,
                               documentId: documentId,
                               email: email,
                               title: "uuden suunnittelijan lis\u00E4\u00E4minen",
                               text: text})
        .success(function() {
          self.email(undefined);
          self.documentName(undefined);
          self.documentId(undefined);
          self.text(undefined);
          self.error(undefined);
          repository.load(id);
          LUPAPISTE.ModalDialog.close();
        })
        .error(function(d) {
          self.error(d.text);
        })
        .call();
      return false;
    };
  }();

  var tab = {
    tabClick: function(data, event) {
      var target = event.target;
     setSelectedTab('#applicationTabs', target);
    }
  };

  function isTabSelected(id) {
    return $(id + ' > li').hasClass("active");
  }

  function selectDefaultTab(id) {
    setSelectedTab(id, $('.active-as-default'));
  }

  function setSelectedTab(id, element) {
    $(id + " li").removeClass("active");
    $(element).parent().addClass("active");
    $(".tab-content").hide();
    var selected_tab = $(element).attr("href");
    $(selected_tab).fadeIn();
  }

  var accordian = function(data, event) {
    accordion.toggle(event);
  };

  var initApplication = function(e) {
    currentId = e.pagePath[0];
    applicationMap.updateSize();
    inforequestMap.updateSize();
    repository.load(currentId);
  };

  hub.onPageChange("application", initApplication);
  hub.onPageChange("inforequest", initApplication);

  $(function() {
    applicationMap = gis.makeMap("application-map", false).center([{x: 404168, y: 6693765}], 12);
    inforequestMap = gis.makeMap("inforequest-map", false).center([{x: 404168, y: 6693765}], 12);

    var bindings = {
      application: application,
      authorities: authorities,
      attachments: attachments,
      attachmentsByGroup: attachmentsByGroup,
      applicationModel: applicationModel,
      comment: commentModel,
      invite: inviteModel,
      authorization: authorizationModel,
      tab: tab,
      accordian: accordian,
      removeDocModel: removeDocModel,
      removeApplicationModel: removeApplicationModel
    };

    ko.applyBindings(bindings, $("#application")[0]);
    ko.applyBindings(bindings, $("#inforequest")[0]);
  });

})();
