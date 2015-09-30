LUPAPISTE.ApplicationsForemanSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchTabsModel(params));

  // TODO check query for notice and verdict
  self.tabs = ko.observableArray(["all",
                                  "application",
                                  "notice",
                                  "inforequest",
                                  "verdict",
                                  "canceled"]);
};
