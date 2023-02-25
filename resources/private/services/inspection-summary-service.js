LUPAPISTE.InspectionSummaryService = function() {
  "use strict";
  var self = this;

  self.serviceName = "inspectionSummaryService";

  self.getTemplatesAsAuthorityAdmin = function() {
    ajax.query("organization-inspection-summary-settings")
      .success(function (event) {
        hub.send(self.serviceName + "::templatesLoaded", event);
      })
      .call();
  };

  self.selectTemplateForOperation = function(operationId, templateId) {
    ajax.command("set-inspection-summary-template-for-operation",
        {operationId: operationId, templateId: templateId || "_unset"})
      .success(function(event) {
        util.showSavedIndicator(event);
        self.getTemplatesAsAuthorityAdmin();
      })
      .call();
  };

  self.modifyTemplate = function(isNew, params, cb) {
    var command = isNew ? "create-inspection-summary-template" : "modify-inspection-summary-template";
    ajax.command(command, params)
      .success(function(event) {
        cb(event);
        self.getTemplatesAsAuthorityAdmin();
      })
      .call();
  };

  self.deleteTemplateById = function(templateId) {
    ajax.command("delete-inspection-summary-template", {templateId: templateId})
      .success(function(event) {
        util.showSavedIndicator(event);
        self.getTemplatesAsAuthorityAdmin();
      })
      .call();
  };
};