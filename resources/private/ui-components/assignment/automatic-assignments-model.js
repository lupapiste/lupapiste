LUPAPISTE.AutomaticAssignmentsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.assignments = params.assignments;

  self.assignmentText = function(assignment) {
    return (assignment.recipient ?
            assignment.recipient.firstName
            + " " + assignment.recipient.lastName + ": "
            : "") + assignment.description + ", "
      + assignment.targets.length + " liitettä";
  };

  self.markComplete = function(assignment) {
    self.sendEvent("assignmentService", "markComplete",
                   {assignmentId: assignment.id, applicationId: params.applicationId});
  };
};
