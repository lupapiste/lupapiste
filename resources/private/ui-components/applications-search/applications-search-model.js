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

  self.searchType = ko.observable("applications");

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
};
