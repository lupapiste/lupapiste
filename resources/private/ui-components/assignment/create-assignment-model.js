LUPAPISTE.CreateAssignmentModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var myService = self.params.myService || "assignmentService";

  self.editorVisible = ko.observable(false);


  self.selectedTargetGroup = ko.observable();
  self.selectedTargetId = ko.observable();
  self.types = [{type: "parties", values: ["foo1", "foo2"]},
                {type: "operations", values: ["op1"]},
                {type: "attachments", values: ["att1, att2"]}]; // TODO data structre

  self.rawTargets = params.targets;
  self.targetGroups = self.disposedPureComputed(function() { return _.keys(self.rawTargets());});
  // query targets
  self.sendEvent(myService, "targetsQuery", {applicationId: params.applicationId()});

  // Possible targets when target group has been selected
  self.targetIds = self.disposedPureComputed(function() {
    var selected = self.selectedTargetGroup();
    if ( selected ) {
      return _.get(self.rawTargets(), selected);
    } else {
      return [];
    }
  });

  self.users = ko.observableArray([]);

  self.recipientId = ko.observable();

  self.description = ko.observable();

  self.mainTypeLoc = function(optionValue) {
    return loc("application.assignment.type." + optionValue);
  };

  self.displayText = function (target) {
    var info = target["info-key"] && loc(target["info-key"]) || target.description;
    return loc(target.type + "._group_label") + (info ? " - " + info : "");
  };

  self.subTypeLabel = self.disposedPureComputed(function() {
    return self.selectedTargetGroup() ? loc("application.assignment.target." + self.selectedTargetGroup()) : loc("application.assignment.type");
  });

  self.assignmentOk = self.disposedPureComputed(function() {
    var observables = [self.selectedTargetGroup(), self.selectedTargetId(), self.recipientId(), self.description()];
    return !_.some(observables, _.isEmpty);
  });

  self.createAssignment = function() {
    self.sendEvent(myService, "createAssignment", {id: params.applicationId(),
                                                   recipientId: self.recipientId(),
                                                   target: { group: self.selectedTargetGroup(),
                                                             id: self.selectedTargetId() },
                                                   description: self.description()});
    self.editorVisible(false);
    self.selectedTargetGroup(undefined);
    self.selectedTargetId(undefined);
    self.description(undefined);
  };

};
