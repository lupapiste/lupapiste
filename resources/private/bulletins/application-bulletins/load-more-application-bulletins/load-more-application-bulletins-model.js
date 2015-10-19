LUPAPISTE.LoadMoreApplicationBulletinsModel = function(params) {
  "use strict";
  var self = this;

  self.requestedPages = params.requestedPages;
  self.bulletinsLeft = params.bulletinsLeft;

  self.showButton = ko.pureComputed(function () {
    return self.bulletinsLeft() > 0;
  });
  
  self.localizedBulletinsLeft = ko.pureComputed(function () {
    return self.bulletinsLeft() + loc("unit.kpl");
  });
};