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
    return (assignment.recipient ?
              assignment.recipient.firstName + " " + assignment.recipient.lastName + ": " :
            "")
      + loc("application.assignment.automatic.target.attachment.message" + (numTargets === 1 ? "" : ".plural"),
            numTargets) + ": ";
  };

  self.assignmentLinkText = function(assignment) {
    return assignment.description;
  };

  self.selectTriggerFilter = function(assignment) {
    var filterSet = self.params.getFilters(self.params.pageName);
    filterSet.toggleAll(false);
    filterSet.getFilterValue("assignment-" + assignment.trigger)(true);
    $("#attachments-accordions")[0].scrollIntoView(true);
  };

  self.markComplete = function(assignment) {
    self.sendEvent("assignmentService", "markComplete",
                   {assignmentId: assignment.id, applicationId: params.applicationId});
  };
};
