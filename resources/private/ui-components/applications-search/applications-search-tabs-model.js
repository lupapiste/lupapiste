LUPAPISTE.ApplicationsSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  self.tabs = ko.observableArray(["all",
                                  "application",
                                  "construction",
                                  "inforequest",
                                  "canceled"]);

  if (lupapisteApp.models.globalAuthModel.ok("archiving-operations-enabled")) {
    self.tabs.push("readyForArchival");
  }

  self.selectedTab = self.dataProvider.applicationType;

  self.selectTab = function(item) {
    hub.send("track-click", {category:"Applications", label: item, event:"radioTab"});
    self.dataProvider.applicationType(item);
    self.dataProvider.skip(0);
  };
};
