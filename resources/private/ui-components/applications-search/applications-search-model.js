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

  var searchResultType =  "all";
  if (lupapisteApp.models.globalAuthModel.ok("user-is-pure-digitizer")) {
    searchResultType = "archivingProjects";
  } else if (lupapisteApp.models.currentUser.isAuthority()) {
    searchResultType = "application";
  }

  self.dataProvider = new LUPAPISTE.ApplicationsDataProvider({
    defaultOperations: self.defaultOperations,
    sort: util.getIn(lupapisteApp.services.applicationFiltersService, ["selected", "sort"]),
    searchResultType: searchResultType,
    currentLimit:     self.currentLimit
  });

  self.externalApi = {
    enabled: ko.pureComputed(function() { return lupapisteApp.models.rootVMO.externalApi.enabled(); }),
    ok: function(fnName) { return lupapisteApp.models.rootVMO.externalApi.ok(fnName); },
    showPermitsOnMap: function() {
      var data = _.map(self.dataProvider.results(), externalApiTools.toExternalPermit);
      hub.send("external-api::filtered-permits", data);
    }
  };

  self.initialized = false;
  self.searchModels = ko.observableArray();

  ko.computed(function() {
    var data = self.authorizationModel.getData();
    if (!_.isEmpty(data) && !self.initialized) {
      self.initialized = true;
      if (!self.authorizationModel.ok("enable-company-search")) {
        self.searchModels.push(new LUPAPISTE.SearchSectionModel({
          type:             "applications",
          lLabel:           "navigation",
          dataProvider:     self.dataProvider,
          externalApi:      self.externalApi,
          limits:           self.limits,
          filterComponent:  "applications-search-filter",
          resultsComponent: "applications-search-results",
          pagingComponent:  "applications-search-paging",
          tabsComponent:    "applications-search-tabs"
        }));
      } else {
        self.searchType("company");
        self.searchModels.push(new LUPAPISTE.SearchSectionModel({
          type:             "company",
          lLabel:           "navigation",
          dataProvider:     self.dataProvider,
          externalApi:      null,
          limits:           self.limits,
          filterComponent:  "applications-company-search-filter",
          resultsComponent: "applications-search-results",
          pagingComponent:  "applications-search-paging",
          tabsComponent:    "applications-company-search-tabs"
        }));
      }
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
        var dataProviderForAssignments = new LUPAPISTE.AssignmentsDataProvider({
                                           sort: util.getIn(lupapisteApp.services.applicationFiltersService, ["selected", "sort"]),
                                           searchResultType: "created",
                                           currentLimit:     self.currentLimit
                                         });
        var label = ko.pureComputed(function() {
          var count = dataProviderForAssignments.assignmentsCount();
          return loc("application.assignment.search.label")
            + (count ? " (" + count + ")" : "");
        });

        self.searchModels.push(new LUPAPISTE.SearchSectionModel({
          type:             "assignments",
          label:            label,
          dataProvider:     dataProviderForAssignments,
          externalApi:      null,
          resultsTextKey:   "application.assignment.search.results",
          limits:           self.limits,
          currentLimit:     self.currentLimit,
          filterComponent:  "assignments-search-filter",
          resultsComponent: "assignments-search-results",
          pagingComponent:  "applications-search-paging",
          tabsComponent:    "assignments-search-tabs"
        }));
      }
    }
  });

  self.searchModel = ko.pureComputed(function () {
    return _.find(self.searchModels(), function (searchModel) {
      return searchModel.type === self.searchType();
    });
  });

  self.totalCount = ko.pureComputed(function() {
    return util.getIn(self.searchModel, ["totalCount"], 0);
  });

  self.noResults = ko.pureComputed(function(){
    return util.getIn(self.searchModel, ["totalCount"]) === 0;
  });

  self.gotResults = ko.pureComputed(function(){
    return util.getIn(self.searchModel, ["gotResults"]);
  });

  self.noApplications = ko.pureComputed(function(){
    return self.dataProvider.initialized()
        && util.getIn(self.searchModel, ["userTotalCount"]) <= 0
        && _.includes(["applications", "company"], util.getIn(self.searchModel, ["type"]));
  });

  self.missingTitle = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.title" : "applications.no-match.title";
  });

  self.missingDesc = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.desc" : "applications.no-match.desc";
  });

  // clear filters when search type is changed.
  // Note: assignments view does not support default filter.
  self.searchType.subscribe(function(val) {
    self.dataProvider.clearFilters();
    if (val === "foreman") {
      self.dataProvider.setDefaultForemanSort();
      lupapisteApp.services.applicationFiltersService.reloadDefaultForemanFilter();
    }
    if (val === "applications"){
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

  self.createArchivingProject = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"createArchivingProject"});
    pageutil.openPage("create-archiving-project");
  };

  self.createArchivingReport = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"createArchivingReport"});
    pageutil.openPage("create-archiving-report");
  };

  hub.onPageLoad("applications", _.wrap( true, self.dataProvider.fetchSearchResults ) );
};
