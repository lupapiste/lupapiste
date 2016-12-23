LUPAPISTE.RootViewModel = function() {
  "use strict";

  var self = this;

  self.currentPage = ko.observable();
  self.previousHash = ko.observable();

  hub.subscribe("page-load", function(data) {
    self.currentPage(data.pageId);
    self.previousHash(data.previousHash);
  });

  self.isCurrentPage = function(page) {
    return page === self.currentPage();
  };

  var apiService = lupapisteApp.services.externalApiService;
  self.externalApi =
    {enabled:  ko.pureComputed(function() { return apiService
                                                   && apiService.enabled
                                                   && lupapisteApp.models.globalAuthModel.ok("external-api-enabled"); }),
     ok: function(functionName) { return _.includes(_.functionsIn(apiService.apiObject), functionName); }};
};
