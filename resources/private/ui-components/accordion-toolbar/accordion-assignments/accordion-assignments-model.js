LUPAPISTE.AccordionAssignmentsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var myService = self.params.myService || "assignmentService";

  self.completeEnabled = lupapisteApp.models.globalAuthModel.ok("complete-assignment");
  self.updateEnabled = lupapisteApp.models.globalAuthModel.ok("update-assignment");

  self.assignments = params.documentAssignments;
  self.possibleTargets = _.get(lupapisteApp.services.assignmentService, "targets");

  self.descriptionText = function(data) {
    return "\"" + data.description + "\"";
  };

  self.receiverName = function(data) {
    if (data.recipient) {
      return  data.recipient.id ? util.partyFullName(data.recipient) : "<"+loc("not-known")+">";
    } else {
      return loc("applications.search.recipient.no-one");
    }
  };

  self.markComplete = function(assignment) {
    self.sendEvent(myService, "markComplete", {assignmentId: assignment.id, applicationId: params.applicationId});
  };

  self.editAssignment = function(assignment) {
    assignment.edit(!assignment.edit()); // toggle
    _.delay(window.Stickyfill.rebuild, 0);
  };

};
