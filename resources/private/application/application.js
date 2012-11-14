/**
 * application.js:
 */

;(function() {

  var applicationModel = new ApplicationModel();
  var authorizationModel = authorization.create();
  var inviteModel = new InviteModel();
  var commentModel = comments.create();
  var documents = ko.observableArray();

  function ApplicationModel() {
    var self = this;

    self.data = ko.observable();

    self.auth = ko.computed(function() {
      var value = [];
      if(self.data() !== undefined) {
        var auth = ko.utils.unwrapObservable(self.data().auth());
        // FIXME: Too complex for jshint, refactor me please:
        var pimped = _.reduce(auth, function(r, i) { var a = r[i.id()] || (i.roles = [], i); a.roles.push(i.role()); r[i.id()] = a; return r;}, {});
        value = _.values(pimped);
      }
      return value;
    },self);
  }

  var application = {
    id: ko.observable(),
    state: ko.observable(),
    location: ko.observable(),
    permitType: ko.observable(),
    title: ko.observable(),
    created: ko.observable(),
    documents: ko.observable(),
    attachments: ko.observableArray(),
    address: ko.observable(),
    verdict: ko.observable(),

    // new stuff
    invites: ko.observableArray(),

    // all data in here
    data: ko.observable(),

    submitApplication: function(model) {
      var applicationId = application.id();
      ajax.command("submit-application", { id: applicationId})
      .success(function(d) {
        notify.success("hakemus j\u00E4tetty",model);
        repository.reloadAllApplications();
      })
      .call();
      return false;
    },

    setMeAsPaasuunnittelija: function(model) {
      var applicationId = application.id();
      ajax.command("user-to-document", { id: applicationId, name: "paasuunnittelija"})
      .success(function(d) {
        notify.success("tiedot tallennettu",model);
        repository.reloadAllApplications();
      })
      .call();
      return false;
    },

    approveApplication: function(model) {
      var applicationId = application.id();
      ajax.command("approve-application", { id: applicationId})
        .success(function(d) {
          notify.success("hakemus hyv\u00E4ksytty",model);
          repository.reloadAllApplications();
        })
        .call();
      return false;
    },

    removeInvite : function(model) {
      var applicationId = application.id();
      ajax.command("remove-invite", { id : applicationId, email : model.user.username()})
        .success(function(d) {
          notify.success("kutsu poistettu", model);
          repository.reloadAllApplications();
        })
        .call();
      return false;
    },

    removeAuth : function(model) {
      var applicationId = application.id();
      ajax.command("remove-auth", { id : applicationId, email : model.username()})
        .success(function(d) {
          notify.success("oikeus poistettu", model);
          repository.reloadAllApplications();
        })
        .call();
      return false;
    }

  };

  var attachments = ko.observableArray([]);

  function makeSubscribable(initialValue, listener) {
    var v = ko.observable(initialValue);
    v.subscribe(listener);
    return v;
  }

  function showApplication(data) {
    authorizationModel.refresh(data,function() {
      // new data mapping
      applicationModel.data(ko.mapping.fromJS(data));
      ko.mapping.fromJS(data, {}, application);

      // comments
      commentModel.setApplicationId(data.id);
      commentModel.setComments(data.comments);

      // docgen:

      var save = function(path, value, callback, data) {
        debug("saving", path, value, data);
        ajax
          .command("update-doc", {doc: data.doc, id: data.app, updates: [[path, value]]})
          .success(function() { callback("ok"); })
          .error(function(e) { error(e); callback(e.status || "err"); })
          .fail(function(e) { error(e); callback("err"); })
          .call();
      };

      var docgenDiv = $("#docgen").empty();

      documents.removeAll();
      _.each(data.documents, function(doc) {
        documents.push(doc);
        docgenDiv.append(docgen.build(doc.schema, doc.body, save, {doc: doc.id, app: application.id()}).element);
      });

      var statuses = {
        requires_user_action: {statusName: "missing"},
        requires_authority_action: {statusName: "new"},
        ok:{statusName: "ok"}
      };

      attachments.removeAll();
      _.each(data.attachments || [], function(a) {
        var s = statuses[a.state] || {statusName: "foo"};
        a.statusName = s.statusName;
        attachments.push(a);
      });

      // Update map:
      var location = application.location();
      hub.send("application-map", {locations: location ? [{x: location.lon(), y: location.lat()}] : []});
      
      pageutil.setPageReady("application");
    });
  }

  hub.subscribe("repository-application-reload", function(e) {
    if (application.id() === e.application.id) showApplication(e.application);
  });

  function InviteModel() {
    var self = this;

    self.email = ko.observable();
    self.text = ko.observable();

    self.submit = function(model) {
      var email = model.email();
      var text = model.text();
      ajax.command("invite", { id: application.id(),
                               email: email,
                               title: "uuden suunnittelijan lis\u00E4\u00E4minen",
                               text: text})
        .success(function(d) {
          self.email(undefined);
          self.text(undefined);
          repository.reloadAllApplications();
        })
        .error(function(d) {
          notify.info("kutsun l\u00E4hett\u00E4minen ep\u00E4onnistui",d);
        })
        .call();
      return false;
    };
  }

  var tab = {
    tabClick: function(data, event) {
     var self = event.target;
     $("#tabs li").removeClass("active");
     $(self).parent().addClass("active");
     $(".tab_content").hide();
     var selected_tab = $(self).attr("href");
     $(selected_tab).fadeIn();
    }
  };

  var accordian = {
    accordianClick: function(data, event) {
     self = event.target;
     $(self).children(".font-icon").toggleClass("icon-collapsed");
     $(self).children(".font-icon").toggleClass("icon-expanded");
     $(self).next(".application_section_content").toggleClass('content_expanded');
    }
  };

  function onPageChange(e) {
    var id = e.pagePath[0];
    if (application.id() != id) {
      repository.getApplication(id, showApplication, function() {
        error("No such application, or not permission: "+id);
        window.location.href = "#!/applications/";
      });
    }
  }

  hub.onPageChange("application", onPageChange);

  $(function() {
    var page = $("#application");
    ko.applyBindings({
      application: application,
      attachments: attachments,
      applicationModel: applicationModel,
      comment: commentModel,
      invite: inviteModel,
      authorization: authorizationModel,
      tab: tab,
      accordian: accordian
    }, page[0]);
  });

})();
