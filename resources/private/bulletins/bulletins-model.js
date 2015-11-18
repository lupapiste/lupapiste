LUPAPISTE.BulletinsModel = function(params) {
  "use strict";
  var self = this;

  var supportedPages = ["bulletins", "bulletin"];

  self.page = ko.observable().extend({
    limited: {values: supportedPages, defaultValue: "bulletins"}
  });
  self.pagePath = ko.observableArray([]);

  var bulletinService = params.bulletinService;

  self.pageParams = ko.pureComputed(function () {
    var defaultParams = {
      bulletinService: bulletinService,
      pagePath: self.pagePath()
    };

    return self.page() === "bulletin" ?
      _.extend(defaultParams, { bulletinId: bulletinId,
                                bulletinService: params.bulletinService }) :
      defaultParams;
  });

  hub.onPageLoad("bulletins", function(e) {
    self.page(e.pageId);
    self.pagePath(e.pagePath);
  });

  hub.onPageLoad("bulletin", function(e) {
    bulletinId = _.first(e.pagePath);
    self.page(e.pageId);
    self.pagePath(e.pagePath);
  });

  self.page(pageutil.getPage());
  self.pagePath(pageutil.getPagePath());
  var bulletinId = pageutil.subPage();
};
