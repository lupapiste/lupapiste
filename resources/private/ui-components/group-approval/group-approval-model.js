// Group (as opposed to document) level approval/reject mechanism
// and visualization.
// Parameters [optional]:
//  docModel: DocModel instance.
//  docModelOptions: options originally passed to DocModel.
//  subSchema: Schema for the group.
//  path: Group path within the document schema.
//  model: model for the group
//  [remove]: {fun, testClass}. See resolveRemoveOptions is docmodel.js
//            for details.

LUPAPISTE.GroupApprovalModel = function( params ) {
  "use strict";
  var self = this;
  var APPROVED = "approved";
  var APPROVE  = "approve";
  var REJECTED = "rejected";
  var REJECT   = "reject";

  self.remove = params.remove || {};
  self.docModel = params.docModel;
  self.model = params.model;
  self.isApprovable = Boolean(params.subSchema.approvable) ;
  self.hasContents = params.remove || self.isApprovable;
  var meta = self.docModel.getMeta( params.path );
  self.approval = ko.observable( meta ? meta._approved : null );

  self.showStatus = ko.pureComputed( _.partial( self.docModel.isApprovalCurrent,
                                                self.model,
                                                self.approval ));

  self.isApproved = ko.pureComputed(_.partial (self.docModel.approvalStatus,
                                               self.approval,
                                               APPROVED));
  self.isRejected = ko.pureComputed(_.partial (self.docModel.approvalStatus,
                                               self.approval,
                                               REJECTED));

  self.testId = _.partial( self.docModel.approvalTestId, params.path );

  function showButton( operation, excluder ) {
    return self.isApprovable
        && self.docModel.authorizationModel.ok( operation + "-doc")
        && (!excluder() || !self.showStatus());
  }

  self.showReject  = ko.pureComputed(_.partial( showButton, REJECT, self.isRejected ));
  self.showApprove = ko.pureComputed(_.partial( showButton, APPROVE, self.isApproved ));

  function changeStatus( flag ) {
    self.docModel.updateApproval( params.path,
                                  flag,
                                  function( approval ) {
                                    self.approval( approval );
                                  } )

  }

  self.reject  = _.partial( changeStatus, false );
  self.approve = _.partial( changeStatus, true );

  self.details = ko.pureComputed( _.partial( self.docModel.approvalInfo,
                                             self.approval ));
}
