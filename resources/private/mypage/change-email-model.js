LUPAPISTE.ChangeEmailModel = function(params) {
  "use strict";
debug("LUPAPISTE.ChangeEmailModel");
  var self = this;
  var superParams = _.merge(params, {accordionContentTemplate: "change-email-template", accordionContentTemplateData: params.data});
  ko.utils.extend(self, new LUPAPISTE.AccordionModel(superParams));
};

// accordion-template is the base template, change-email-template is set above as an extension
ko.components.register("change-email", {viewModel: LUPAPISTE.ChangeEmailModel, template: {element: "accordion-template"}});
