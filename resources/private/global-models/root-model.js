LUPAPISTE.RootViewModel = function() {
  "use strict";

  var self = this;

  self.currentPage = ko.observable();
  self.previousHash = ko.observable();

  hub.subscribe("page-load", function(data) {
    self.currentPage(data.pageId);
    self.previousHash(data.previousHash);
  });
};
