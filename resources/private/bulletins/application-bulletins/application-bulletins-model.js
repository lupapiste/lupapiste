LUPAPISTE.ApplicationBulletinsModel = function(params) {
  "use strict";
  var self = this;
  self.bulletinService = params.bulletinService;

  self.query = {
    searchText: ko.observable(),
    municipality: ko.observable(),
    page: ko.observable(1)
  };

  self.openBulletin = function(item) {
    pageutil.openPage("bulletin", item.id);
  };

  // Reset page when search filters change
  ko.computed(function() {
    self.query.searchText();
    self.query.page(1);
  });

  self.moreBulletinsPending = ko.observable(false);

  ko.computed(function() {
    self.bulletinService.fetchBulletins(ko.mapping.toJS(self.query),
      self.moreBulletinsPending);
  });
};
