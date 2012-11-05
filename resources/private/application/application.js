/**
 * application.js:
 */

;(function() {

  var applicationModel = new ApplicationModel();
  var authorizationModel = new AuthorizationModel();
  var inviteModel = new InviteModel();
  var commentModel = new CommentModel();
  var documents = ko.observableArray();

  mapintegration.whenOskariMapIsReady(function() {
  	mapintegration.moveOskariMapToDiv("application-map");
    refreshMap();
  });

  var icon = (function() {
    var size = new OpenLayers.Size(21,25);
    var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);
    return new OpenLayers.Icon('/img/marker-green.png', size, offset);
  })();

  function refreshMap() {
  	mapintegration.clearMapWithDelay(refreshMapPoints);
  }

  function refreshMapPoints() {
    // FIXME Hack: we'll have to wait 100ms
    setTimeout(function() {
      var mapPoints = [];

      if (application.id() && application.location()) {
        mapPoints.push({
          id: "markerFor" + application.id(),
          location: {x: application.location().lon(), y: application.location().lat()}
        });
      }

      hub.send("documents-map", {
        data : mapPoints
      });
    }, 99);
  }

  function ApplicationModel() {
    var self = this;

    self.data = ko.observable();

    self.auth = ko.computed(function() {
      var value = [];
      if(self.data() != undefined) {
        var auth = ko.utils.unwrapObservable(self.data().auth());
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
    comments: ko.observable(),
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

  function makeSubscribable(initialValue, listener) {
    var v = ko.observable(initialValue);
    v.subscribe(listener);
    return v;
  }

  function showApplication(data) {
    ajax.query("allowed-actions",{id: data.id})
      .success(function(d) {
        authorizationModel.data(d.actions);
        showApplicationPart2(data);
        pageutil.setPageReady("application");
      })
      .call();
  }

  function showApplicationPart2(data) {

    // new data mapping
    applicationModel.data(ko.mapping.fromJS(data));

    ko.mapping.fromJS(data, {}, application);

  	// docgen:

    var save = function(path, value, callback, data) {
      debug("saving", path, value, data);
      ajax
      	.command("update-doc", {app: application.id(), doc: data.doc, updates: [[path, value]]})
      	.success(function() { callback("ok"); })
      	.error(function(e) { callback(e.status); })
      	.fail(function(e) { callback("err"); })
      	.call();
    };

    var docgenDiv = $("#docgen").empty();

    documents.removeAll();
  	$.each(data.documents, function(id, doc) {
  		documents.push(doc);
  		docgenDiv.append(docgen.build(doc.schema, doc.body, save, {doc: "fozzaa"}).element);
  	});

		application.attachments(_.values(data.attachments)); 
  }

  function uploadCompleted(file, size, type, attachmentId) {
    // if (attachments) attachments.push(new Attachment(file, type, size, attachmentId));
  }

  hub.subscribe("repository-application-reload", function(e) {
    if (application.id() === e.applicationId) {
      repository.getApplication(e.applicationId, showApplication, function() {
        // TODO: Show "No such application, or not permission"
        error("No such application, or not permission");
      });
    }
  });

  function AuthorizationModel() {
    var self = this;

    self.data = ko.observable({})

    self.ok = function(command) {
      return self.data && self.data()[command] && self.data()[command].ok;
    }
  }

  function CommentModel() {
    var self = this;

    self.text = ko.observable();

    self.disabled = ko.computed(function() { return _.isEmpty(self.text());});

    self.submit = function(model) {
    var applicationId = application.id();
      ajax.command("add-comment", { id: applicationId, text: model.text()})
      .success(function(d) {
        repository.reloadAllApplications();
          model.text("");
          })
          .call();
      return false;
    }
  };

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
    }
  }

  var tab = {
      tabClick: function(data, event) {
         var self = event.target;
         $("#tabs li").removeClass('active');
         $(self).parent().addClass("active");
         $(".tab_content").hide();
         var selected_tab = $(self).attr("href");
         $(selected_tab).fadeIn();
      }
  };

  var accordian = {
      accordianClick: function(data, event) {
         self = event.target;
         $(self).next(".application_section_content").toggleClass('content_expanded');
      }
  };

  function onPageChange(e) {
    var id = e.pagePath[0];
    if (application.id() != id) {
      repository.getApplication(id, showApplication, function() {
        // TODO: Show "No such application, or not permission"
        error("No such application, or not permission");
      });
    }

  }

  $(function() {
    hub.onPageChange("application", function(e) { onPageChange(e);});

    var page = $("#application");

    ko.applyBindings({
      application: application,
      applicationModel: applicationModel,
      comment: commentModel,
      invite: inviteModel,
      authorization: authorizationModel,
      tab: tab,
      accordian: accordian
    }, page[0]);

  });

})();
