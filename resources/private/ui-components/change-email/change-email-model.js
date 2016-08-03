LUPAPISTE.ChangeEmailModel = function(params) {
  "use strict";
  var self = this;
  self.newEmail = ko.observable("").extend({email: true});
  self.validated = ko.validatedObservable([self.newEmail]);
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.proceed = ko.observable(false);
  self.email = params.data.email;

  self.authorized = _.partial(params.authorization.ok, "change-email-init");
  self.ok = ko.pureComputed(function() {
    return self.newEmail() && self.validated.isValid() && self.newEmail() !== self.email() && self.authorized() && !self.processing();
  });

  self.save = function() {
    // Edge might allow button to be clicked even if it is disabled, check again!
    if (self.ok()) {
      ajax.command("change-email-init", {email: self.newEmail()})
        .success(_.partial(self.proceed, true))
        .error(util.showSavedIndicator)
        .processing(self.processing)
        .pending(self.pending)
        .call();
    }
  };

  // Extend the accordion component, render content with change-email-template.
  // Parameters for the parent component:
  var superParams = {ltitle: params.ltitle,
                     accordionContentTemplate: "change-email-template",
                     accordionContentTemplateData: self};
  ko.utils.extend(self, new LUPAPISTE.AccordionModel(superParams));

  // Initialize new email with current value
  self.disposedComputed(function() {
    var userEmail = params.data.email();
    self.newEmail(userEmail);
  });
};
