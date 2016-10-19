LUPAPISTE.CreateAssignmentModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var myService = self.params.myService || "assignmentService";

  self.editorVisible = ko.observable(false);


  self.selectedType = ko.observable();
  self.selectedSubtype = ko.observable();
  self.types = [{type: "parties", values: ["foo1", "foo2"]},
                {type: "operations", values: ["op1"]},
                {type: "attachments", values: ["att1, att2"]}]; // TODO data structre

  self.subTypes = ko.pureComputed(function() {
    var selected = self.selectedType();
    if ( selected ) {
      return _.find(self.types, {type: selected}).values;
    } else {
      return;
    }
  });

  self.users = ko.observableArray([]);

  self.mainTypeLoc = function(optionValue) {
    return loc("application.assignment.type." + optionValue.type);
  };


  self.createAssignment = function(v) {
    self.sendEvent(myService, "createAssignment", v);
    self.editorVisible(false);
  };

};
