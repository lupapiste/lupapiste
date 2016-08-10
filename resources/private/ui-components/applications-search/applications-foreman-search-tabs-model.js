LUPAPISTE.ApplicationsForemanSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchTabsModel(params));

  // TODO check query for notice and verdict
  self.tabs = ko.observableArray(["all",
                                  "foremanApplication",
                                  "foremanNotice",
                                  "inforequest",
                                  "verdict",
                                  "canceled"]);

  if (lupapisteApp.models.globalAuthModel.ok("archiving-operations-enabled")) {
    self.tabs.push("readyForArchival");
  }
};
