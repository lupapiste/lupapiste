// Group (as opposed to document) level approval/reject mechanism
// and visualization.
// Parameters [optional]:
//  docModel: DocModel instance.
//  subSchema: Schema for the group.
//  path: Group path within the document schema.
//  model: model for the group
//  [remove]: {fun, attr}. See resolveRemoveOptions in docmodel.js
//            for details.

LUPAPISTE.GroupApprovalModel = function( params ) {
  "use strict";
  var self = this;

  var APPROVE  = "approve";
  var REJECT   = "reject";

  var service = lupapisteApp.services.documentApprovalService;

  self.remove = params.remove || {};
  self.docModel = params.docModel;
  self.path = params.path;
  self.model = params.model;
  self.isApprovable = Boolean(params.subSchema.approvable) ;

  self.approval = function() {
    return service.groupApproval( self.docModel.docId, self.path );
  };

  ko.utils.extend(self, new LUPAPISTE.ApprovalModel(self));

  self.details = self.disposedPureComputed( _.partial( self.approvalInfo, self.approval ));

  self.testId = _.partial( self.docModel.approvalTestId, params.path );

  function showButton( operation, excluder ) {
    return self.isApprovable
        && self.docModel.authorizationModel.ok( operation + "-doc")
        && (!excluder() || !self.showStatus());
  }

  self.showReject  = self.disposedPureComputed(_.partial( showButton, REJECT, self.isRejected ));
  self.showApprove = self.disposedPureComputed(_.partial( showButton, APPROVE, self.isApproved ));

  self.hasContents = self.disposedPureComputed( function() {
    var a = self.approval();
    return a.value || !_.isBlank( a.note )
      || self.showReject() || self.showApprove()
      || self.remove.fun;
  });

  function changeStatus( flag ) {
    self.docModel.updateApproval( params.path, flag );
    hub.send( "approval-status", {docId: self.docModel.docId,
                                  path: params.path,
                                  approval: {value: flag ? "approved" : "rejected"}});
  }

  self.reject  = _.partial( changeStatus, false );
  self.approve = _.partial( changeStatus, true );
};
