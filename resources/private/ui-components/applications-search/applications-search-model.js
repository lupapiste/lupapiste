LUPAPISTE.ApplicationsDataProvider = function() {
  "use strict";

  var self = this;

  var defaultData = {applications: [],
                     totalCount: -1,
                     userTotalCount: -1};

  self.data = ko.observable(defaultData);
  self.applications = ko.observableArray([]);

  self.applicationType = ko.observable("all");

  self.searchField = ko.observable("");
  self.searchFieldDelayed = ko.pureComputed(self.searchField).extend({rateLimit: {method: "notifyWhenChangesStop", timeout: 750}});

  self.handler = ko.observable();

  self.applicationTags = ko.observableArray([]);

  self.areas = ko.observableArray([]);

  self.limit = ko.observable(25);

  self.sort = {field: ko.observable("modified"), asc: ko.observable(false)};

  self.skip = ko.observable(0);

  self.pending = ko.observable(false);
  ko.computed(function() {
    return self.pending() ? pageutil.showAjaxWait(loc("applications.loading")) : pageutil.hideAjaxWait();
  });

  self.onSuccess = function(res) {
        self.data(res.data);
        self.applications(res.data.applications);
  };

  ko.computed(function() {
    ajax.datatables("applications-search",
               {searchText: self.searchFieldDelayed(),
                applicationTags: _.pluck(self.applicationTags(), "id"),
                handler: self.handler() ? self.handler().id : undefined,
                applicationType: self.applicationType(),
                areas: self.areas(),
                limit: self.limit(),
                sort: ko.mapping.toJS(self.sort),
                skip: self.skip()})
      .success(self.onSuccess)
      .pending(self.pending)
    .call();
  }).extend({rateLimit: 0}); // http://knockoutjs.com/documentation/rateLimit-observable.html#example-3-avoiding-multiple-ajax-requests


  hub.onPageLoad("applications", function() {
    ajax.datatables("applications-search",
               {searchText: self.searchField(),
                applicationTags: _.pluck(self.applicationTags(), "id"),
                handler: self.handler() ? self.handler().id : undefined,
                applicationType: self.applicationType(),
                areas: self.areas(),
                limit: self.limit(),
                sort: ko.mapping.toJS(self.sort),
                skip: self.skip()})
      .success(self.onSuccess)
    .call();
  });

};

LUPAPISTE.ApplicationsSearchModel = function() {
  "use strict";

  var self = this;

  self.dataProvider = new LUPAPISTE.ApplicationsDataProvider();

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

  self.create = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"create"});
    pageutil.openPage("create-part-1");
  };
  self.createWithPrevPermit = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"createWithPrevPermit"});
    pageutil.openPage("create-page-prev-permit");
  };
};
