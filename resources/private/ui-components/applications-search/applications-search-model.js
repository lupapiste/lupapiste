LUPAPISTE.ApplicationsSearchModel = function() {
  "use strict";

  var self = this;

  self.searchType = ko.observable("applications");

  self.defaultOperations = function() {
    return self.searchType() === "foreman" ? [{id: "tyonjohtajan-nimeaminen-v2", label: ""}, {id: "tyonjohtajan-nimeaminen", label: ""}] : [];
  };

  self.dataProvider = new LUPAPISTE.ApplicationsDataProvider({defaultOperations: self.defaultOperations});

  self.noApplications = ko.pureComputed(function(){
    return self.dataProvider.data().userTotalCount <= 0;
  });

  self.totalCount = ko.pureComputed(function() {
    return self.dataProvider.data().totalCount;
  });

  self.noResults = ko.pureComputed(function(){
    return self.totalCount() === 0;
  });

  self.gotResults = ko.pureComputed(function(){
    return self.totalCount() > 0;
  });

  self.missingTitle = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.title" : "applications.no-match.title";
  });

  self.missingDesc = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.desc" : "applications.no-match.desc";
  });

  self.authorizationModel = lupapisteApp.models.globalAuthModel;

  self.limits = ko.observableArray([10, 25, 50, 100]);

  // clear filters when search type is changed
  self.searchType.subscribe(function(val) {
    self.dataProvider.clearFilters();
    if (val === "foreman") {
      self.dataProvider.setDefaultForemanSort();
      lupapisteApp.services.applicationFiltersService.reloadDefaultForemanFilter();
    } else {
      self.dataProvider.setDefaultSort();
      lupapisteApp.services.applicationFiltersService.reloadDefaultFilter();
    }
    self.dataProvider.updateApplicationType( val );
  });

  self.showForemanSearch = ko.pureComputed(function() {
    return self.searchType() === "foreman";
  });

  self.showApplicationsSearch = ko.pureComputed(function() {
    return self.searchType() === "applications";
  });

  self.create = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"create"});
    pageutil.openPage("create-part-1");
  };

  self.createWithPrevPermit = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"createWithPrevPermit"});
    pageutil.openPage("create-page-prev-permit");
  };

  self.externalApi = {
    enabled: ko.pureComputed(function() {
      return lupapisteApp.models.rootVMO.externalApiEnabled() &&
             lupapisteApp.models.globalAuthModel.ok("external-api-enabled");
    }),
    showPermitsOnMap: function() {
      var data = _.map(self.dataProvider.applications(), externalApiTools.toExternalPermit);
      hub.send("external-api::filtered-permits", data);
    }
  };

};
