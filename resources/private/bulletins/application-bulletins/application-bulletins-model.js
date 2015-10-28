LUPAPISTE.ApplicationBulletinsModel = function(params) {
  "use strict";
  var self = this;
  self.bulletinService = params.bulletinService;

  self.isLoadButtonPending = ko.pureComputed(function() {
    return self.bulletinService.fetchBulletinsPending();
  });

  self.isLoadButtonVisible = ko.pureComputed(function() {
    return self.bulletinService.bulletinsLeft() > 0;
  });

};
