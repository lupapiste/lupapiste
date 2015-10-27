LUPAPISTE.LoadMoreApplicationBulletinsModel = function(params) {
  "use strict";
  var self = this;

  self.requestedPages = params.requestedPages;

  self.bulletinsLeft = params.bulletinsLeft;
  self.pending = ko.observable();

  self.showButton = ko.pureComputed(function () {
    return self.bulletinsLeft() > 0;
  });

  self.localizedBulletinsLeft = ko.pureComputed(function () {
    return self.bulletinsLeft() + loc("unit.kpl");
  });

  ko.computed(function() {
    hub.send("bulletinService::fetchBulletins", {
      page: self.requestedPages(),
      pending: self.pending
    });
  });
};
