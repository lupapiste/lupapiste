LUPAPISTE.AssignmentsSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchTabsModel(params));

  self.tabs = ko.observableArray(["all",
                                  "created",
                                  "completed"]);
};
