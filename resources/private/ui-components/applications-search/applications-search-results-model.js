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

  self.gotResults = params.gotResults;

  self.selectedTab = ko.observable("all");

  self.selectTab = function(item) {
    self.dataProvider.applicationType(item);
    self.selectedTab(item);
  };

  self.sortBy = function(target) {
    var sortObj = self.dataProvider.sort;
    if ( target === sortObj.field() ) {
      sortObj.asc(!sortObj.asc()); // toggle direction
    } else {
      sortObj.field(target);
      sortObj.asc(false);
    }
  };
};
