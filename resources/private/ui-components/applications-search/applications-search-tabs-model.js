LUPAPISTE.ApplicationsSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  if (lupapisteApp.models.globalAuthModel.ok("user-is-pure-digitizer")) {
    self.tabs = ko.observableArray();
  } else if (lupapisteApp.models.currentUser.isFinancialAuthority()) {
    self.tabs = ko.observableArray(["all", "application", "construction", "canceled"]);
  } else {
    self.tabs = ko.observableArray(["all", "application", "construction", "inforequest", "canceled"]);
  }


  if (lupapisteApp.models.globalAuthModel.ok("archiving-operations-enabled")) {
    self.tabs.push("readyForArchival");
  }

  if (lupapisteApp.models.globalAuthModel.ok("digitizing-enabled")) {
    self.tabs.push("archivingProjects");
  }

  self.selectedTab = self.dataProvider.searchResultType;

  self.selectTab = function(item) {
    hub.send("track-click", {category:"Applications", label: item, event:"radioTab"});
    self.dataProvider.searchResultType(item);
    self.dataProvider.skip(0);
  };
};
