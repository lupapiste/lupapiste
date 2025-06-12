// Display the review request information and provice cancel request functionality.
// Parameters:
//   taskId: Id of the task
//   details: Request details (see review-tasks-model.js)
LUPAPISTE.ReviewRequestSummaryModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.taskService;
  var taskId = params.taskId;

  self.buildings = params.details.buildings;
  self.message = params.details.message;
  self.contact = params.details.contact;

  self.canCancel = self.disposedPureComputed( _.partial( service.reviewAuthOk,
                                                         taskId, "cancel-review-request" ));

  self.cancelRequest = function() {
    hub.send( "show-dialog",
              {ltitle: "areyousure",
               size: "medium",
               component: "yes-no-dialog",
               componentParams: {ltext: "review-request.cancel.confirmation",
                                 yesFn: _.wrap( taskId, service.cancelReviewRequest )}});
  };
};
