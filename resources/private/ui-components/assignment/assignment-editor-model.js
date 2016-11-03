LUPAPISTE.AssignmentEditorModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var myService = self.params.myService || "assignmentService";

  self.editorVisible = params.visible || ko.observable(true);
  self.edit = params.editMode;

  // Interesting properties
  self.assignmentId = ko.observable(params.assignmentId);
  self.selectedTargetGroup = ko.observable(params.targetGroup);
  self.selectedTargetId = ko.observable(params.targetId);
  self.recipientId = ko.observable(params.recipientId);
  self.description = ko.observable(params.description);

  self.authorities = params.authorities || [];
  self.rawTargets = params.targets;
  self.targetGroups = self.disposedPureComputed(function() { return _.keys(self.rawTargets());});

  // Possible targets when target group has been selected
  self.targetIds = self.disposedPureComputed(function() {
    var selected = self.selectedTargetGroup();
    if ( selected ) {
      return _.get(self.rawTargets(), selected);
    } else {
      return [];
    }
  });

  self.mainTypeLoc = function(optionValue) {
    return loc("application.assignment.type." + optionValue);
  };

  self.subTypeLabel = self.disposedPureComputed(function() {
    return self.selectedTargetGroup() ? loc("application.assignment.target." + self.selectedTargetGroup()) : loc("application.assignment.type");
  });

  self.assignmentOk = self.disposedPureComputed(function() {
    var observables = [self.selectedTargetGroup(), self.selectedTargetId(), self.recipientId(), self.description()];
    return !_.some(observables, _.isEmpty);
  });

  self.saveAssignment = function() {
    self.sendEvent(myService, "saveAssignment", {id: util.getIn(params, ["applicationId"]),
                                                 assignmentId: self.assignmentId(),
                                                 recipientId: self.recipientId(),
                                                 target: [self.selectedTargetGroup(), self.selectedTargetId()],
                                                 description: self.description()});
    self.editorVisible(false);
  };


};
