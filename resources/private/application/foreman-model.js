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
    // TODO
    // 1. invite foreman to current application (new role-parameter to invite command)
    // 2. create "työnjohtajan ilmoitus" application (new link-parameter to create command)
    // 3. invite foreman to new application
    // 4. open new application
    self.processing(true);
    self.pending(true);
    setTimeout(function() {
      console.log("foo");
      self.processing(false);
      self.pending(false);
      LUPAPISTE.ModalDialog.close();
    }, 2500)
  }
}
