LUPAPISTE.CompanyApproveInviteDialogModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.YesNoDialogModel(params));

  self.applySubmitRestriction = params.applySubmitRestriction;

};
