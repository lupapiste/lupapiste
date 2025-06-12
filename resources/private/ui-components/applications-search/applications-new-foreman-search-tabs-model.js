LUPAPISTE.ApplicationsNewForemanSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchTabsModel(params));

  self.tabs(["foreman-all",
             "declared",
             "submitted",
             "approved",
             "acknowledged",
             "rejected"]);
};
