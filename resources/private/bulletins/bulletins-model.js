LUPAPISTE.BulletinsModel = function() {
  "use strict";
  var self = this;

  self.page = ko.observable();
  self.pageParams = ko.observable({});

  hub.onPageLoad("bulletins", function() {
    self.pageParams({});
    self.page("bulletins");
  });

  hub.onPageLoad("bulletin", function(event) {
    self.pageParams({bulletinId: event.pagePath[0]});
    self.page("bulletin");
  });


  self.page("bulletins");

};
