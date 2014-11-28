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

  self.refresh = function(application) {
    self.application = application;
  }

  self.inviteForeman = function() {
    LUPAPISTE.ModalDialog.open('#dialog-invite-foreman')
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
      // 2. create "työnjohtajan ilmoitus" application
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
