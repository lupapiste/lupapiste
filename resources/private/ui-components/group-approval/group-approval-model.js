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

  function modelModifiedSince(model, timestamp) {
    if (model) {
      if (!timestamp) {
        return true;
      }
      if (!_.isObject(model)) {
        return false;
        }
      if (_.has(model, "value")) {
        // Leaf
        return model.modified && model.modified > timestamp;
      }
      return _.find(model, function (myModel) { return modelModifiedSince(myModel, timestamp); });
    }
    return false;
  }

  self.remove = params.remove || {};
  self.docModel = params.docModel;
  self.model = params.model;
  self.isApprovable = Boolean(params.subSchema.approvable) ;
  self.hasContents = params.remove || self.isApprovable;
  var meta = self.docModel.getMeta( params.path );
  self.approval = ko.observable( meta ? meta._approved : null );

  self.showStatus = ko.pureComputed(function () {
    // Status is shown only if it applies to the current model state.
    var result = self.approval() && !modelModifiedSince( self.model, self.approval().timestamp );
    return result;
  });

  // check is either rejected or approved.
  function isStatus( check ) {
    var result = self.approval() && self.approval().value === check;
    return result;
  }

  self.isApproved = ko.pureComputed(_.partial (isStatus, APPROVED));
  self.isRejected = ko.pureComputed(_.partial (isStatus, REJECTED));

  self.testId = function( verb ) {
    return params.docModelOptions && params.docModelOptions.dataTestSpecifiers
         ? [verb, "doc", _.first( params.path) || self.docModel.schemaName].join( "-" )
         : "";
  }

  function showButton( operation, excluder ) {
    return self.isApprovable
        && self.docModel.authorizationModel.ok( operation + "-doc")
        && (!excluder() || !self.showStatus());
  }

  self.showReject   = ko.pureComputed(_.partial ( showButton, REJECT, self.isRejected ));
  self.showApprove = ko.pureComputed(_.partial ( showButton, APPROVE, self.isApproved ));

  function changeStatus( flag ) {
    self.docModel.updateApproval( params.path,
                                  flag,
                                  function( approval ) {
                                    self.approval( approval );
                                  } )

  }

  self.reject  = _.partial( changeStatus, false );
  self.approve = _.partial( changeStatus, true );

  self.details = ko.pureComputed( function() {
    var app = self.approval();
    if(app && app.user && app.timestamp) {
      var text = loc(["document", app.value]);
      text += " (" + app.user.lastName + " "
            + app.user.firstName
            + " " + moment(app.timestamp).format("D.M.YYYY HH:mm") + ")";
      return text;
    }
  });
}
