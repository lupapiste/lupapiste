LUPAPISTE.RootViewModel = function() {
  "use strict";

  var self = this;

  self.currentPage = ko.observable();
  self.previousHash = ko.observable();

  hub.subscribe("page-load", function(data) {
    self.currentPage(data.pageId);
    self.previousHash(data.previousHash);
  });

  self.externalApiEnabled = ko.pureComputed(function() {
    return lupapisteApp.services.externalApiService &&
           lupapisteApp.services.externalApiService.enabled &&
           features.enabled("louhipalvelin");
  });
};
