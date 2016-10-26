LUPAPISTE.AssignmentsSearchTabsModel = function(params) {
  "use strict";
  var self = this;
  console.log("initializing AssignmentsSearchTabsModel");

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchTabsModel(params));

  self.tabs = ko.observableArray(["all",
                                  "active",
                                  "completed"]);
  self.selectedTab = ko.observable("all");
};
