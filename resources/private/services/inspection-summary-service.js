LUPAPISTE.InspectionSummaryService = function() {
  "use strict";
  var self = this;

  self.serviceName = "inspectionSummaryService";

  self.getTemplatesAsAuthorityAdmin = function() {
    ajax.query("organization-inspection-summary-templates")
      .success(function (event) {
        hub.send(self.serviceName + "::templatesLoaded", event);
      })
      .call();
  };

  self.deleteTemplateById = function(templateId) {
    ajax.command("modify-inspection-summary-template", {func: "delete", templateId: templateId})
      .success(function(event) {
        util.showSavedIndicator(event);
        self.getTemplatesAsAuthorityAdmin();
      })
      .call();
  }
};