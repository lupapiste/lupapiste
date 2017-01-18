LUPAPISTE.InspectionSummaryTemplateBubbleModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.templateId = ko.observable();
  self.bubbleVisible = params.bubbleVisible;
  self.functionCode = params.functionCode;
  self.name = ko.observable("");
  self.templateText = ko.observable("");
  self.templateId = ko.observable("");

  if (params.template) {
    self.name(params.template.name || "");
    self.templateText(params.template.templateText || "");
    self.templateId(params.template.id);
  }

  self.okVisible = ko.observable(true);
  self.cancelVisible = ko.observable(true);

  self.isValid = self.disposedPureComputed(function() {
    return !_.isEmpty(self.name()) && !_.isEmpty(self.templateText());
  });

  self.call = function() {
    ajax.command("modify-inspection-summary-template",
      {func: self.functionCode, name: self.name(), templateText: self.templateText(), templateId: self.templateId()})
      .success(function(event) {
        util.showSavedIndicator(event);
        self.bubbleVisible(false);
        self.name("");
        self.templateText("");
        lupapisteApp.services.inspectionSummaryService.getTemplatesAsAuthorityAdmin();
      })
      .call();
  };

};