LUPAPISTE.CreateAssignmentModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());
  var myService = self.params.myService || "assignmentService";

  self.authorities = self.params.authorities;
  self.applicationId = self.params.applicationId;
  self.targets = self.params.targets;
  self.initialTarget = self.params.initialTarget;

  self.editorVisible = ko.observable(false);

  // query targets from service
  self.sendEvent(myService, "targetsQuery", {applicationId: self.applicationId()});


};
