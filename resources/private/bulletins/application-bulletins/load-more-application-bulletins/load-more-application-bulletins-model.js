LUPAPISTE.LoadMoreApplicationBulletinsModel = function(params) {
  "use strict";
  var self = this;

  self.bulletinsLeft = params.bulletinsLeft;
  self.pending = ko.observable();

  self.showButton = ko.pureComputed(function () {
    return self.bulletinsLeft() > 0;
  });

  self.localizedBulletinsLeft = ko.pureComputed(function () {
    return self.bulletinsLeft() + loc("unit.kpl");
  });

  self.pageChanged = function() {
    hub.send("bulletinService::pageChanged");
  };

  var id = hub.subscribe("bulletinService::pendingChanged", function(event) {
    self.pending(event.pending);
  });

  self.dispose = function () {
    hub.unsubscribe(id);
  };
};
