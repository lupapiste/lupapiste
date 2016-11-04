LUPAPISTE.AccordionAssignmentsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var myService = self.params.myService || "assignmentService";

  function enrichAccordionAssignment(assignment) {
    return _.merge(assignment, {edit: ko.observable(false)});
  }

  self.assignments = ko.observableArray(_.map(params.documentAssignments(), enrichAccordionAssignment));
  self.possibleTargets = lupapisteApp.services.assignmentService.targets;

  self.descriptionText = function(data) {
    return "\"" + data.description + "\"";
  };

  self.markComplete = function(assignment) {
    self.sendEvent(myService, "markComplete", {assignmentId: assignment.id, applicationId: params.applicationId});
  };

  self.editAssignment = function(assignment) {
    assignment.edit(!assignment.edit()); // toggle
    _.delay(window.Stickyfill.rebuild, 0);
  };

};
