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
};