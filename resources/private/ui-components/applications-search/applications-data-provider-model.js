LUPAPISTE.ApplicationsDataProvider = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var defaultData = {applications: [],
                     totalCount: -1,
                     userTotalCount: -1};

  var defaultSort = {field: "modified", asc: false};

  var defaultForemanSort = {field: "submitted", asc: false};

  var defaultOperations = params.defaultOperations;

  // Observables
  self.sort = util.getIn(lupapisteApp.services.applicationFiltersService, ["selected", "sort"]) ||
              {field: ko.observable(defaultSort.field), asc: ko.observable(defaultSort.asc)};

  self.data = ko.observable(defaultData);

  self.applications = ko.observableArray([]);

  self.applicationType = ko.observable(lupapisteApp.models.currentUser.isAuthority()
                                       ? "application"
                                       : "all");

  self.searchField = ko.observable("");

  self.searchFieldDelayed = ko.pureComputed(self.searchField).extend({rateLimit: {method: "notifyWhenChangesStop", timeout: 750}});

  self.limit = ko.observable(100); // Default limit applications per page

  self.skip = ko.observable(0);

  self.pending = ko.observable(false);

  // Application <-> foremanApplication
  // foremanNotice -> application
  self.updateApplicationType = function( searchType ) {
    var current = self.applicationType();
    if( searchType === "foreman" && current === "application") {
      self.applicationType( "foremanApplication");
    } else {
      if( searchType === "applications" && _.startsWith( current, "foreman")) {
        self.applicationType( "application");
      }
    }
  };

  // Computed
  var searchFields = ko.pureComputed(function() {
    var operations = lupapisteApp.services.operationFilterService.selected();
    if (_.isEmpty(operations)) {
      operations = defaultOperations();
    }

    return { searchText: self.searchFieldDelayed(),
             tags: _.map(lupapisteApp.services.tagFilterService.selected(), "id"),
             organizations: _.map(lupapisteApp.services.organizationFilterService.selected(), "id"),
             operations: _.map(operations, "id"),
             handlers: _.map(lupapisteApp.services.handlerFilterService.selected(), "id"),
             applicationType: self.applicationType(),
             areas: _.map(lupapisteApp.services.areaFilterService.selected(), "id"),
             limit: self.limit(),
             sort: ko.mapping.toJS(self.sort),
             skip: self.skip() };
  }).extend({rateLimit: 0});

  self.disposedComputed(function() {
    self.searchFieldDelayed();
    lupapisteApp.services.tagFilterService.selected();
    lupapisteApp.services.organizationFilterService.selected();
    lupapisteApp.services.operationFilterService.selected();
    lupapisteApp.services.handlerFilterService.selected();
    self.applicationType();
    lupapisteApp.services.areaFilterService.selected();
    self.limit();
    self.skip(0); // when above filters change, set table view to first page
  });

  ko.computed(function() {
    return self.pending() ? pageutil.showAjaxWait(loc("applications.loading")) : pageutil.hideAjaxWait();
  });

  // Subscribtions
  lupapisteApp.services.applicationFiltersService.selected.subscribe(function(selected) {
    if (selected) {
      self.sort.field(selected.sort.field());
      self.sort.asc(selected.sort.asc());
    }
  });

  // Methods
  function wrapData(data) {
    data.applications = _.map(data.applications, function(item) {
      switch(item.urgency) {
        case "urgent":
          item.urgencyClass = "lupicon-warning";
          break;
        case "normal":
          item.urgencyClass = "lupicon-document-list";
          break;
        case "pending":
          item.urgencyClass = "lupicon-circle-dash";
          break;
      }
      return item;
    });
    return data;
  }

  self.onSuccess = function(res) {
    var data = wrapData(res.data);
    self.data(data);
    self.applications(data.applications);
  };

  self.clearFilters = function() {
    lupapisteApp.services.handlerFilterService.selected([]);
    lupapisteApp.services.tagFilterService.selected([]);
    lupapisteApp.services.operationFilterService.selected([]);
    lupapisteApp.services.organizationFilterService.selected([]);
    lupapisteApp.services.areaFilterService.selected([]);
    lupapisteApp.services.applicationFiltersService.selected(undefined);
    self.searchField("");
  };

  self.setDefaultSort = function() {
    self.sort.field(defaultSort.field);
    self.sort.asc(defaultSort.asc);
  };

  self.setDefaultForemanSort = function() {
    self.sort.field(defaultForemanSort.field);
    self.sort.asc(defaultForemanSort.asc);
  };

  hub.onPageLoad("applications", function() {
    ajax.datatables("applications-search", searchFields())
      .success(self.onSuccess)
      .onError("error.unauthorized", notify.ajaxError)
      .pending(self.pending)
      .call();
  });

  ko.computed(function() {
    ajax.datatables("applications-search", searchFields())
      .success(self.onSuccess)
      .onError("error.unauthorized", notify.ajaxError)
      .pending(self.pending)
      .call();
  }).extend({rateLimit: 0}); // http://knockoutjs.com/documentation/rateLimit-observable.html#example-3-avoiding-multiple-ajax-requests
};
