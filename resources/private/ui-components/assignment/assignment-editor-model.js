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

  self.authorities = self.disposedPureComputed(function() {
    return _.concat({id: ""}, self.params.authorities() || []);
  });
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

  self.displayText = function (target) {
    var info = target["info-key"] && loc(target["info-key"]) || target.description;
    return loc(target["type-key"]) + (info ? " - " + info : "");
  };

  self.subTypeLabel = self.disposedPureComputed(function() {
    return self.selectedTargetGroup() ? loc("application.assignment.target." + self.selectedTargetGroup()) : loc("application.assignment.type");
  });

  self.assignmentOk = self.disposedPureComputed(function() {
    var observables = [self.selectedTargetGroup(), self.selectedTargetId(), self.description()];
    return !_.some(observables, _.isEmpty);
  });

  self.saveAssignment = function() {
    self.sendEvent(myService, "saveAssignment", {id: util.getIn(params, ["applicationId"]),
                                                 assignmentId: self.assignmentId(),
                                                 recipientId: self.recipientId() || "",
                                                 targets: [{ group: self.selectedTargetGroup(),
                                                             id: self.selectedTargetId() }],
                                                 description: self.description()});
    self.editorVisible(false);
  };

  // refresh targets from service
  self.sendEvent(myService, "targetsQuery", {applicationId: util.getIn(params, ["applicationId"])});

  self.receiverName = function(receiver) {
    return receiver.id ? util.partyFullName(receiver) : loc("applications.search.recipient.no-one");
  };
};
