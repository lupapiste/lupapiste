LUPAPISTE.IntegrationErrorDialogModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.OkDialogModel(params));
  self.technicalDetails = params.details || "";
};
