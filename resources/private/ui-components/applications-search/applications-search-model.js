LUPAPISTE.ApplicationsSearchModel = function() {
  "use strict";

  var self = this;

  self.authorizationModel = lupapisteApp.models.globalAuthModel;

  self.searchType = ko.observable("applications");

  self.defaultOperations = function() {
    return self.searchType() === "foreman" ? [{id: "tyonjohtajan-nimeaminen-v2", label: ""}, {id: "tyonjohtajan-nimeaminen", label: ""}] : [];
  };

  self.limits = ko.observableArray([10, 25, 50, 100]);
  self.currentLimit = ko.observable(100);

  self.dataProvider = new LUPAPISTE.ApplicationsDataProvider({
    defaultOperations: self.defaultOperations,
    sort: util.getIn(lupapisteApp.services.applicationFiltersService, ["selected", "sort"]),
    searchResultType: lupapisteApp.models.currentUser.isAuthority() ? "application" : "all",
    currentLimit:     self.currentLimit
  });

  self.externalApi = {
    enabled: ko.pureComputed(function() {
      return lupapisteApp.models.rootVMO.externalApiEnabled() &&
             lupapisteApp.models.globalAuthModel.ok("external-api-enabled");
    }),
    showPermitsOnMap: function() {
      var data = _.map(self.dataProvider.results(), externalApiTools.toExternalPermit);
      hub.send("external-api::filtered-permits", data);
    }
  };


  self.searchModels =
    ko.observableArray([new LUPAPISTE.SearchSectionModel({
      type:             "applications",
      lLabel:           "navigation",
      dataProvider:     self.dataProvider,
      externalApi:      self.externalApi,
      limits:           self.limits,
      filterComponent:  "applications-search-filter",
      resultsComponent: "applications-search-results",
      pagingComponent:  "applications-search-paging",
      tabsComponent:    "applications-search-tabs"
    })]);
  if (self.authorizationModel.ok("enable-foreman-search")) {
    self.searchModels.push(new LUPAPISTE.SearchSectionModel({
      type:             "foreman",
      lLabel:           "applications.search.foremen",
      dataProvider:     self.dataProvider,
      externalApi:      null,
      limits:           self.limits,
      filterComponent:  "applications-foreman-search-filter",
      resultsComponent: "applications-foreman-search-results",
      pagingComponent:  "applications-search-paging",
      tabsComponent:    "applications-foreman-search-tabs"
    }));
  }
  if (self.authorizationModel.ok("assignments-search")) {
    self.searchModels.push(new LUPAPISTE.SearchSectionModel({
      type:             "assignments",
      lLabel:           "application.assignment.search.label",
      dataProvider:     new LUPAPISTE.AssignmentsDataProvider({
        sort: util.getIn(lupapisteApp.services.applicationFiltersService, ["selected", "sort"]),
        searchResultType: "active",
        currentLimit:     self.currentLimit
      }),
      externalApi:      null,
      resultsTextKey:   "application.assignment.search.results",
      limits:           self.limits,
      currentLimit:     self.currentLimit,
      filterComponent:  "applications-search-filter",
      resultsComponent: "assignments-search-results",
      pagingComponent:  "applications-search-paging",
      tabsComponent:    "assignments-search-tabs"
    }));
  }

  self.searchModel = ko.pureComputed(function () {
    return _.find(self.searchModels(), function (searchModel) {
      return searchModel.type === self.searchType();
    });
  });

  self.totalCount = ko.pureComputed(function() {
    return self.searchModel().totalCount();
  });

  self.noResults = ko.pureComputed(function(){
    return self.searchModel().totalCount() === 0;
  });

  self.gotResults = ko.pureComputed(function(){
    return self.searchModel().gotResults();
  });

  self.noApplications = ko.pureComputed(function(){
    return self.searchModel().userTotalCount() <= 0;
  });

  self.missingTitle = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.title" : "applications.no-match.title";
  });

  self.missingDesc = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.desc" : "applications.no-match.desc";
  });

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
    self.dataProvider.updateSearchResultType( val );
  });

  self.create = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"create"});
    pageutil.openPage("create-part-1");
  };

  self.createWithPrevPermit = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"createWithPrevPermit"});
    pageutil.openPage("create-page-prev-permit");
  };
};
