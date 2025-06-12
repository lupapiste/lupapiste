// Proxy model for the for the documentApprovalService.
LUPAPISTE.DocumentApprovalModel = function(docModel) {
  "use strict";

  var self = this;

  var service = lupapisteApp.services.documentApprovalService;

  self.approval = function() {
    return service.documentApproval( docModel.docId );
  };

  ko.utils.extend(self, new LUPAPISTE.ApprovalModel( self ));

  self.isSummaryRejected = self.disposedPureComputed( function() {
    return self.approval().isSummaryRejected;
  });

  self.showStatus = self.disposedPureComputed( function() {
    var a = self.approval();
    return a.value || !_.isBlank( a.note );
  });

  self.changeStatus = function(flag) {
    docModel.updateApproval([], flag);
    hub.send("document-approval", {docId: docModel.docId, approved: flag});
  };
};
