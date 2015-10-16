LUPAPISTE.ApplicationBulletinsModel = function(params) {
  "use strict";
  var self = this;
  self.bulletinService = new LUPAPISTE.ApplicationBulletinsService();
  self.requestedPages = ko.observable(1);

  self.openBulletin = function(item) {
    pageutil.openPage("bulletin", item.id);
  };

  ko.computed(function () {
    self.bulletinService.fetchBulletins(self.requestedPages());
  });
};