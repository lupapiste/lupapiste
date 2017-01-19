// Parameters:
// bubbleVisible (mandatory): visibility observable to be passed on to bubble-dialog
// functionCode (mandatory): create or update - new template or editor for an existing one
// template: object representing a template to be edited
LUPAPISTE.InspectionSummaryTemplateBubbleModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.templateId = ko.observable();
  self.bubbleVisible = params.bubbleVisible;
  self.functionCode = params.functionCode;
  self.name = ko.observable(_.get( params, "template.name", "" ));
  self.templateText = ko.observable(_.get( params, "template.templateText", "" ));
  self.templateId = ko.observable(_.get( params, "template.templateId", "" ));

  self.okVisible = ko.observable(true);
  self.cancelVisible = ko.observable(true);

  self.isValid = self.disposedPureComputed(function() {
    return !_.isEmpty(self.name()) && !_.isEmpty(self.templateText());
  });

  self.call = function() {
    lupapisteApp.services.inspectionSummaryService.modifyTemplate(
      {func: self.functionCode,
       name: self.name(),
       templateText: self.templateText(),
       templateId: self.templateId()},
       function(event) {
         util.showSavedIndicator(event);
         self.bubbleVisible(false);
         self.name("");
         self.templateText("");
         lupapisteApp.services.inspectionSummaryService.getTemplatesAsAuthorityAdmin();
       });
  };

};