LUPAPISTE.ApplicationsSearchResultsModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;
  self.data = self.dataProvider.applications;
  self.tabs = ko.observableArray(["all",
                                  "application",
                                  "construction",
                                  "inforequest",
                                  "canceled"]);

  self.totalCount = ko.pureComputed(function() {
    return self.dataProvider.data() ? self.dataProvider.data().totalCount : 0;
  });

  self.selectedTab = ko.observable("all");

  self.selectTab = function(item) {
    self.dataProvider.applicationType(item);
    self.selectedTab(item);
  };
};
