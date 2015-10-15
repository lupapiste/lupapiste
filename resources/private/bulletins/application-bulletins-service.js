LUPAPISTE.ApplicationBulletinsService = function() {
  "use strict";
  var self = this;

  self.data = ko.observableArray([]);
  self.bulletinsLeft = ko.observable(0);

  self.fetchBulletins = function (page) {
    ajax.datatables("application-bulletins", {page: page})
      .success(function(res) {
        self.bulletinsLeft(res.left);
        self.data(self.data().concat(res.data));
      })
      .call();
  };
};
