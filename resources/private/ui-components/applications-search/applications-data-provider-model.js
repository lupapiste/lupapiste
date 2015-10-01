LUPAPISTE.ApplicationsDataProvider = function() {
  "use strict";

  var self = this;

  var defaultData = {applications: [],
                     totalCount: -1,
                     userTotalCount: -1};

  var defaultSort = {field: "modified", asc: false};

  var defaultForemanSort = {field: "submitted", asc: false};

  // Observables
  self.sort = util.getIn(lupapisteApp.services.applicationFiltersService, ["selected", "sort"]) ||
              {field: ko.observable(defaultSort.field), asc: ko.observable(defaultSort.asc)};

  self.data = ko.observable(defaultData);

  self.applications = ko.observableArray([]);

  self.applicationType = ko.observable("all");

  self.searchField = ko.observable("");

  self.searchFieldDelayed = ko.pureComputed(self.searchField).extend({rateLimit: {method: "notifyWhenChangesStop", timeout: 750}});

  self.limit = ko.observable(25);

  self.skip = ko.observable(0);

  self.pending = ko.observable(false);

  // Computed
  var searchFields = ko.pureComputed(function() {
    return { searchText: self.searchFieldDelayed(),
             tags: _.pluck(lupapisteApp.services.tagFilterService.selected(), "id"),
             organizations: _.pluck(lupapisteApp.services.organizationFilterService.selected(), "id"),
             operations: _.pluck(lupapisteApp.services.operationFilterService.selected(), "id"),
             handlers: _.pluck(lupapisteApp.services.handlerFilterService.selected(), "id"),
             applicationType: self.applicationType(),
             areas: _.pluck(lupapisteApp.services.areaFilterService.selected(), "id"),
             limit: self.limit(),
             sort: ko.mapping.toJS(self.sort),
             skip: self.skip() };
  }).extend({rateLimit: 0});

  ko.computed(function() {
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
    console.log("setDefaultForemanSort");
    self.sort.field(defaultForemanSort.field);
    self.sort.asc(defaultForemanSort.asc);
  };

  hub.onPageLoad("applications", function() {
    ajax.datatables("applications-search", searchFields())
      .success(self.onSuccess)
    .call();
  });

  ko.computed(function() {
    ajax.datatables("applications-search", searchFields())
      .success(self.onSuccess)
      .pending(self.pending)
    .call();
  }).extend({rateLimit: 0}); // http://knockoutjs.com/documentation/rateLimit-observable.html#example-3-avoiding-multiple-ajax-requests
};
