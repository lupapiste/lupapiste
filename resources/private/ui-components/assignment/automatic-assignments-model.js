LUPAPISTE.AutomaticAssignmentsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.assignments = params.assignments;
  self.authModel = params.authModel;

  function uniqueTargetsLength(assignment) {
    return _.uniqBy(assignment.targets, "id").length;
  }

  self.assignmentText = function(assignment) {
    var numTargets = uniqueTargetsLength(assignment);
    var name = _.join( _.filter( [_.get( assignment, "recipient.firstName"),
                                  _.get( assignment, "recipient.lastName")]),
                       " ");
    return (name ? name + ": " : "")
      + loc("application.assignment.automatic.target.attachment.message" + (numTargets === 1 ? "" : ".plural"),
            numTargets) + ": ";
  };

  self.assignmentLinkText = function(assignment) {
    return assignment.description;
  };

  self.selectTriggerFilter = function(assignment) {
    hub.send( "automaticAssignment::selected", {assignmentId: assignment.id });
    return false;
  };

  self.markComplete = function(assignment) {
    self.sendEvent("assignmentService", "markComplete",
                   {assignmentId: assignment.id, applicationId: params.applicationId});
  };
};
