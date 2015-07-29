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

  self.handler = ko.observable();

  self.applicationTags = ko.observableArray([]);

  self.onSuccess = function(res) {
        self.data(res.data);
        self.applications(res.data.applications);
  };

  ko.computed(function() {
    ajax.datatables("applications-search",
               {searchText: self.searchField(),
                applicationTags: self.applicationTags(),
                handler: self.handler() ? self.handler().id : undefined,
                applicationType: self.applicationType()})
      .success(self.onSuccess)
    .call();
  }).extend({throttle: 250});


  hub.onPageLoad("applications", function() {
    ajax.datatables("applications-search",
               {searchText: self.searchField(),
                applicationTags: self.applicationTags(),
                handler: self.handler() ? self.handler().id : undefined,
                applicationType: self.applicationType()})
      .success(self.onSuccess)
    .call();
  });

};

LUPAPISTE.ApplicationsSearchModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = new LUPAPISTE.ApplicationsDataProvider();

  self.noApplications = ko.pureComputed(function(){
    return self.dataProvider.data().userTotalCount <= 0;
  });

  self.noResults = ko.pureComputed(function(){
    return self.dataProvider.data().totalCount === 0;
  });

  self.gotResults = ko.pureComputed(function(){
    return self.dataProvider.data().totalCount > 0;
  });

  self.missingTitle = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.title" : "applications.no-match.title";
  });
  self.missingDesc = ko.pureComputed(function() {
    return self.noApplications() ? "applications.empty.desc" : "applications.no-match.desc";
  });

  self.authorizationModel = lupapisteApp.models.globalAuthModel;

  self.create = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"create"});
    pageutil.openPage("create-part-1");
  };
  self.createWithPrevPermit = function() {
    hub.send("track-click", {category:"Applications", label:"create", event:"createWithPrevPermit"});
    pageutil.openPage("create-page-prev-permit");
  };
};
