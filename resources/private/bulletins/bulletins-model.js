LUPAPISTE.BulletinsModel = function(params) {
  "use strict";
  var self = this;

  var supportedPages = ["bulletins", "bulletin"];

  self.page = ko.observable().extend({
    limited: {values: supportedPages, defaultValue: "bulletins"}
  });
  self.pagePath = ko.observableArray([]);

  var bulletinService = params.bulletinService;
  var vetumaService = params.vetumaService;
  var fileuploadService = params.fileuploadService;
  var auth = params.auth;

  self.bulletinId = ko.observable(pageutil.subPage());

  self.pageParams = {bulletinService: bulletinService,
                     authenticated: vetumaService.authenticated,
                     pagePath: self.pagePath,
                     bulletinId: self.bulletinId,
                     userInfo: vetumaService.userInfo,
                     fileuploadService: fileuploadService,
                     auth: auth};

  hub.onPageLoad("bulletins", function(e) {
    self.page(e.pageId);
    self.pagePath(e.pagePath);
    window.lupapisteApp.setTitle("Julkipano");
    hub.send("bulletinService::fetchBulletins");
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
