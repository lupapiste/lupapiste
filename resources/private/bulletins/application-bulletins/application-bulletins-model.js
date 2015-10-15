LUPAPISTE.ApplicationBulletinsModel = function() {
  "use strict";
  var self = this;
  self.bulletinService = new LUPAPISTE.ApplicationBulletinsService();
  self.requestedPages = ko.observable(1);

  self.openBulletin = function(item) {
  	console.log(item);
  };

  ko.computed(function () {
    self.bulletinService.fetchBulletins(self.requestedPages());
  });
};