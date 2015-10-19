LUPAPISTE.BulletinsModel = function() {
  "use strict";
  var self = this;

  self.supportedPages = ["bulletins", "bulletin"];

  self.page = ko.observable().extend({limited: {values: self.supportedPages, defaultValue: "bulletins"}});

  self.pageParams = ko.observable({});

  hub.onPageLoad("bulletins", function() {
    self.pageParams({});
    self.page("bulletins");
  });

  hub.onPageLoad("bulletin", function(event) {
    self.pageParams({bulletinId: event.pagePath[0]});
    self.page("bulletin");
  });

  function onLoad() {
    var currPage = pageutil.getPage();
    if (currPage === "bulletin") {
      self.pageParams({bulletinId: pageutil.subPage()});
      self.page("bulletin");
    } else {
      self.page(currPage);
    }
  }

  onLoad();
};
