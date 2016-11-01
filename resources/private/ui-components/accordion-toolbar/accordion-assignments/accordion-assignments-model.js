LUPAPISTE.AccordionAssignmentsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  //var myService = self.params.myService || "assignmentService";

  self.assignments = params.documentAssignments;

  self.descriptionText = function(data) {
    return "\"" + data.description + "\"";
  };

  self.getCreator = function(assignment) {
    var createdState = _.find(assignment.states, function(state) {
      return state.type === "created";
    });
    return createdState.user;
  };

};
