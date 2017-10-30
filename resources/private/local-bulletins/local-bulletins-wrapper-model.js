LUPAPISTE.LocalBulletinsWrapperModel = function() {
  "use strict";
  var self = this;

  var supportedPages = ["local-bulletins", "bulletin"];

  self.page = ko.observable().extend({
    limited: {values: supportedPages, defaultValue: "local-bulletins"}
  });

/*  var vetumaService = params.vetumaService;
  var fileuploadService = params.fileuploadService;
  var auth = params.auth; */

  self.bulletinId = ko.observable(pageutil.subPage());
  self.pageParams = {};
  /*self.pageParams = {bulletinService: bulletinService,
                     authenticated: vetumaService.authenticated,
                     pagePath: self.pagePath,
                     bulletinId: self.bulletinId,
                     userInfo: vetumaService.userInfo,
                     fileuploadService: fileuploadService,
                     auth: auth}; */

  hub.onPageLoad("local-bulletins", function(e) {
    self.page(e.pageId);
    window.lupapisteApp.setTitle("Julkipano");
  });

  hub.onPageLoad("bulletin", function(e) {
    self.bulletinId(_.head(e.pagePath));
    self.page(e.pageId);
    window.lupapisteApp.setTitle("Julkipano");
  });

  self.page(pageutil.getPage());
};