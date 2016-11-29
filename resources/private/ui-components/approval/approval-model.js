LUPAPISTE.ApprovalModel = function(params) {
  "use strict";

  var self = this;
  var APPROVED = "approved";
  var REJECTED = "rejected";

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));


  // Textual representation of the approval status.
  // Tiedot OK (Sibbo Sonja 21.9.2015 10:55)
  self.approvalInfo = function(approvalData) {
    var approval = ko.unwrap(approvalData);
    var text = null;
    if(approval && approval.user && approval.timestamp) {
      text = loc(["document", self.isApproved() ? APPROVED : REJECTED]);
      text += " (" + approval.user.lastName + " "
      + approval.user.firstName
      + " " + moment(approval.timestamp).format("D.M.YYYY HH:mm") + ")";
    }
    return text;
  };

  if( params.approval) {
    self.approval = params.approval;

    // Check is either approved or rejected.
    // Note: if the approval is not set, then both types of check return false
    var approvalStatus = function(approvalFun, check) {
      var approval = approvalFun();
      return approval && approval.value === check;
    };

    self.isApproved = self.disposedPureComputed(_.partial(approvalStatus, self.approval, APPROVED));
    self.isRejected = self.disposedPureComputed(_.partial(approvalStatus, self.approval, REJECTED));
    self.showStatus = self.disposedPureComputed(_.partial(util.getIn, self.approval, ["value"]));
    self.details = self.disposedPureComputed(_.partial(self.approvalInfo, self.approval));
  }
  if( params.attachment ) {
    console.log( "Attachment:", params.attachment);
    var attachment = params.attachment;
    var service = lupapisteApp.services.attachmentsService;

    self.isApproved = self.disposedPureComputed( _.wrap( attachment(), service.isApproved ));
    self.isRejected = self.disposedPureComputed( _.wrap( attachment(), service.isRejected ));
    self.showStatus = self.disposedPureComputed( function() {
      return self.isApproved() || self.isRejected();
    });
    var fileId = util.getIn( attachment, ["latestVersion", "fileId"]);
    self.details = self.disposedPureComputed( _.wrap( util.getIn( attachment,
                                                                  ["approvals", fileId],
                                                                  _.noop),
                                                    self.approvalInfo));
    console.log( "rejected:", self.isRejected(), "approved:", self.isApproved(), "details:", self.details());
  }
};
