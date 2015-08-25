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

  self.selectedTab = self.dataProvider.applicationType;

  self.selectTab = function(item) {
    hub.send("track-click", {category:"Applications", label: item, event:"radioTab"});
    self.selectedTab(item);
    self.dataProvider.skip(0);
  };

  self.sortBy = function(target) {
    self.dataProvider.skip(0);
    var sortObj = self.dataProvider.sort;
    if ( target === sortObj.field() ) {
      sortObj.asc(!sortObj.asc()); // toggle direction
    } else {
      sortObj.field(target);
      sortObj.asc(false);
    }
  };

  self.openApplicationTargeted = function(model, event, target) {
    pageutil.openApplicationPage(model, target);
  };
};
