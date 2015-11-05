LUPAPISTE.BulletinsModel = function(params) {
  "use strict";
  var self = this;

  var supportedPages = ["bulletins", "bulletin"];

  self.page = ko.observable().extend({
    limited: {values: supportedPages, defaultValue: "bulletins"}
  });

  var bulletinService = params.bulletinService;

  self.pageParams = ko.pureComputed(function () {
    var defaultParams = {
      bulletinService: bulletinService
    };

    return self.page() === "bulletin" ?
      _.extend(defaultParams, { bulletinId: bulletinId,
                                bulletinService: params.bulletinService }) :
      defaultParams;
  });

  hub.onPageLoad("bulletins", function(e) {
    self.page(e.pageId);
  });

  hub.onPageLoad("bulletin", function(e) {
    bulletinId = _.first(e.pagePath);
    self.page(e.pageId);
  });

  self.page(pageutil.getPage());
  var bulletinId = pageutil.subPage();
};
