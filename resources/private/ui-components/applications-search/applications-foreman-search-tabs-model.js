LUPAPISTE.ApplicationsForemanSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  self.tabs = ko.observableArray(["all",
                                  "application",
                                  "notice",
                                  "inforequest",
                                  "verdict",
                                  "canceled"]);

  self.selectedTab = self.dataProvider.applicationType;

  self.selectTab = function(item) {
    hub.send("track-click", {category:"Applications", label: item, event:"radioTab"});
    self.dataProvider.applicationType(item);
    self.dataProvider.skip(0);
  };
};
