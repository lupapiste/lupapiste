LUPAPISTE.ApplicationsSearchTabsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.tabs = ko.observableArray();

  self.dataProvider = params.dataProvider;

  var authOk = lupapisteApp.models.globalAuthModel.ok;

  if (authOk("user-is-pure-digitizer")) {
    self.tabs([]);
  } else if (lupapisteApp.models.currentUser.isFinancialAuthority()) {
    self.tabs(["all", "application", "construction", "verdict", "finalized", "canceled"]);
  } else if (authOk("user-is-pure-ymp-org-user")) {
      self.tabs(["all", "application", "verdict", "finalized", "inforequest", "canceled"]);
  } else {
    self.tabs(_.filter([authOk( "processing-dashboard-tab") ? "processing" : null,
                        "all", "application",
                        features.enabled( "purkamisilmoitus" ) ? "demolition-notice" : null,
                        "construction", "verdict", "finalized", "inforequest", "canceled"]));
  }

  if (authOk("archiving-operations-enabled")) {
    self.tabs.push("readyForArchival");
  }

  if (authOk("digitizing-enabled")) {
    self.tabs.push("archivingProjects");
  }

  self.selectedTab = self.dataProvider.searchResultType;
  self.selectedStates = self.dataProvider.selectedStates;

  self.toggles = self.disposedComputed( function () {
    return _.map( self.tabs(),
                  function( t ) {
                    return {value: t,
                            testId: "search-tab-" + t,
                            lText: "applications.filter." + t};
                  });

  });

  self.disposedSubscribe( self.selectedTab,
                          function(item) {
                            self.dataProvider.searchResultType(item);
                            self.dataProvider.skip(0);
                          });
};
