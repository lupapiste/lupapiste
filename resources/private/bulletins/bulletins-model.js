LUPAPISTE.BulletinsModel = function(params) {
  "use strict";
  var self = this;

  var supportedPages = ["bulletins", "bulletin"];

  self.page = ko.observable().extend({
    limited: {values: supportedPages, defaultValue: "bulletins"}
  });
  self.pagePath = ko.observableArray([]);

  var bulletinService = params.bulletinService;

  self.bulletinId = ko.observable(pageutil.subPage());

  self.pageParams = {bulletinService: bulletinService,
                     pagePath: self.pagePath,
                     bulletinId: self.bulletinId};

  hub.onPageLoad("bulletins", function(e) {
    self.page(e.pageId);
    self.pagePath(e.pagePath);
    window.lupapisteApp.setTitle("Julkipano");
  });

  hub.onPageLoad("bulletin", function(e) {
    self.bulletinId(_.first(e.pagePath));
    self.page(e.pageId);
    self.pagePath(e.pagePath);
    window.lupapisteApp.setTitle("Julkipano");
    hub.send("bulletinService::fetchBulletin", {id: self.bulletinId()});
  });

  self.page(pageutil.getPage());
  self.pagePath(pageutil.getPagePath());
};
