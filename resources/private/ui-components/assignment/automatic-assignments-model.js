LUPAPISTE.AutomaticAssignmentsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.assignments = params.assignments;
  self.authModel = params.authModel;
  self.noLink = params.noLink;

  self.filteredAssignments = self.disposedPureComputed(function() {
    return _.isFunction(params.filterFn) ? _.filter(self.assignments(), params.filterFn) : self.assignments();
  });

  function uniqueTargetsLength(assignment) {
    return _.uniqBy(assignment.targets, "id").length;
  }

  self.assignmentText = function(assignment) {
    var numTargets = uniqueTargetsLength(assignment);
    var name = _.join( _.filter( [_.get( assignment, "recipient.firstName"),
                                  _.get( assignment, "recipient.lastName")]),
                       " ");
    var locKey = "application.assignment.automatic.target.attachment.message";
    if (assignment.trigger === "bim-model") {
      locKey = "application.assignment.automatic.target.bim.message";
    }
    return (name ? name + ": " : "") + loc(locKey + (numTargets === 1 ? "" : ".plural"), numTargets) + ": ";
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
