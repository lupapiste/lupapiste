LUPAPISTE.ApplicationsCompanySearchTabsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ApplicationsSearchTabsModel(params));

  self.tabs(["all",
             "application",
             "construction",
             "inforequest",
             "canceled"]);

};
