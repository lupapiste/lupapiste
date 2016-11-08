LUPAPISTE.AssignmentTargetFilterService = function() {
  "use strict";
  var self = this;

  self.selected = ko.observableArray([]);

  self.data = function() {
    var targets = ["parties", "documents", "attachments"];
    return _.map(targets, function (t) { return {id: t, label: loc("application.assignment.type." + t) }; });
  };

};