LUPAPISTE.LoadMoreApplicationBulletinsModel = function(params) {
  "use strict";
  var self = this;

  self.dataRemaining = params.dataRemaining;
  self.pending = params.pending;
  self.visible = params.visible;

  self.localizedBulletinsLeft = ko.pureComputed(function () {
    return self.dataRemaining() + " " + loc("unit.kpl");
  });

  self.pageChanged = function() {
    hub.send("bulletinService::pageChanged");
  };
};
