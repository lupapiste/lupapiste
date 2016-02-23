LUPAPISTE.ChangeEmailModel = function(params) {
  "use strict";
  var self = this;
  self.newEmail = ko.observable("").extend({email: true});
  self.validated = ko.validatedObservable([self.newEmail]);

  // TODO change command name after a new command is added
  self.authorized = _.partial(params.authorization.ok, "change-passwd");
  self.ok = ko.pureComputed(function() {
    return self.newEmail() && self.validated.isValid() && self.newEmail() !== params.data.email() && self.authorized();
  });

  // Extend the accordion component, render content with change-email-template.
  // Parameters for the parent component:
  var superParams = {ltitle: params.ltitle,
                     accordionContentTemplate: "change-email-template",
                     accordionContentTemplateData: _.merge(params.data, _.pick(self, ["newEmail", "authorized", "ok"]))};
  ko.utils.extend(self, new LUPAPISTE.AccordionModel(superParams));

  // Initialize new email with current value
  self.disposedComputed(function() {
    var userEmail = params.data.email();
    self.newEmail(userEmail);
  });
};

// accordion-template is the base template, change-email-template is set above as an extension
ko.components.register("change-email", {viewModel: LUPAPISTE.ChangeEmailModel, template: {element: "accordion-template"}});
