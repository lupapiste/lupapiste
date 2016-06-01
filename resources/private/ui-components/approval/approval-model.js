LUPAPISTE.ApprovalModel = function(params) {
  "use strict";

  var self = this;
  var APPROVED = "approved";
  var REJECTED = "rejected";

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  // Check is either approved or rejected.
  // Note: if the approval is not set, then both types of check return false
  var approvalStatus = function(approvalFun, check) {
    var approval = approvalFun();
    return approval && approval.value === check;
  };

  // Textual representation of the approval status.
  // Tiedot OK (Sibbo Sonja 21.9.2015 10:55)
  self.approvalInfo = function(approvalFun) {
    var approval = approvalFun();
    var text = null;
    if(approval && approval.user && approval.timestamp) {
      text = loc(["document", approval.value]);
      text += " (" + approval.user.lastName + " "
      + approval.user.firstName
      + " " + moment(approval.timestamp).format("D.M.YYYY HH:mm") + ")";
    }
    return text;
  };

  self.approval = params.approval;

  self.isApproved = self.disposedPureComputed(_.partial(approvalStatus, self.approval, APPROVED));
  self.isRejected = self.disposedPureComputed(_.partial(approvalStatus, self.approval, REJECTED));
  self.showStatus = self.disposedPureComputed(_.partial(util.getIn, self.approval, ["value"]));
  self.details = self.disposedPureComputed(_.partial(self.approvalInfo, self.approval));
};
