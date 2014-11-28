LUPAPISTE.ForemanModel = function() {
  "use strict";
  var self = this;
  self.application = null;
  self.email = ko.observable();
  self.error = ko.observable();
  self.processing = ko.observable();
  self.pending = ko.observable();
  self.disabled = ko.computed(function() {
    return self.email() && !util.isValidEmailAddress(self.email());
  });
  self.foremanApplications = ko.observableArray();

  self.refresh = function(application) {
    function loadForemanApplications(applications) {
      self.foremanApplications([]);
      _.forEach(_.pluck(applications, "id"), function(id) {
        ajax
        .query("application", {id: id})
        .success(function(app) {
          var data = {"state": app.application.state,
                      "id": app.application.id};
          var docs = _.where(app.application.documents, {"schema-info": {"name": "tyonjohtaja"}});
          _.forEach(docs, function(doc) {
            var firstName =
            data['firstname'] = doc.data.henkilotiedot ? doc.data.henkilotiedot.etunimi ? doc.data.henkilotiedot.etunimi.value : undefined : undefined;
            data['lastname'] = doc.data.henkilotiedot ? doc.data.henkilotiedot.sukunimi ? doc.data.henkilotiedot.sukunimi.value : undefined : undefined;
            data['email'] = doc.data.yhteystiedot ? doc.data.yhteystiedot.email ? doc.data.yhteystiedot.email.value : undefined : undefined;
            self.foremanApplications.push(data);
            // TODO sort array by id
          });
        })
        .call();
      })
    }

    self.application = application;
    _.defer(function() {
      loadForemanApplications(_.where(application.linkPermitData, { "operation": "tyonjohtajan-nimeaminen" }));
    });
  }

  self.inviteForeman = function() {
    LUPAPISTE.ModalDialog.open('#dialog-invite-foreman')
  }

  self.openApplication = function(id) {
    repository.load(id);
    window.location.hash = "!/application/" + id;
  }

  self.submit = function(model) {
    self.error(undefined);

    function inviteToApplication(id, cb, errorCb) {
      if (!errorCb) {
        errorCb = cb;
      }

      ajax.command("invite", { id: id,
                               documentName: "",
                               documentId: "",
                               path: "",
                               email: self.email(),
                               title: "",
                               text: "" })
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          cb(data);
        })
        .error(function(err) {
          errorCb(err);
        })
        .call();
    }

    function linkToApplication(id) {
      // 3. Link new application to current
      ajax.command("add-link-permit", { id: self.application.id,
                                        linkPermitId: id,
                                        propertyId: self.application.propertyId })
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
          LUPAPISTE.ModalDialog.close();
          // 4. open new application
          repository.load(id);
          window.location.hash = "!/application/" + id;
        })
        .error(function(err) {
          self.error(loc(err.text))
        })
        .call();
      return false;
    }

    function createApplication() {
      // 2. create "tyonjohtajan ilmoitus" application
      ajax.command("create-application", { infoRequest: false,
                                           operation: "tyonjohtajan-nimeaminen",
                                           y: self.application.location.y,
                                           x: self.application.location.x,
                                           address: self.application.address,
                                           propertyId: self.application.propertyId,
                                           messages: [],
                                           municipality: self.application.municipality })
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          // 3. invite foreman to new application
          if (self.email()) {
            inviteToApplication(data.id, function() {
              linkToApplication(data.id);
            }, function(err) {
              self.error(loc(err.text));
            });
          } else {
            linkToApplication(data.id);
          }
        })
        .error(function(err) {
          self.error(loc(err.text));
        })
        .call();
    }
    // 1. invite foreman to current application (new role-parameter to invite command)
    if (self.email()) {
      inviteToApplication(self.application.id, createApplication, createApplication)
    } else {
      createApplication();
    }
    return false;
  }
}
