LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;
  self.data = self.dataProvider.data;
  self.tabs = ko.observableArray(["all",
                                  "applications",
                                  "postVerdict",
                                  "infoRequest"]);

  self.selectedTab = ko.observable("all");

  self.selectTab = function(item) {
    self.dataProvider.applicationType(item);
    self.selectedTab(item);
  };
};
