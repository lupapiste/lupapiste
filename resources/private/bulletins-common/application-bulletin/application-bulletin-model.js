LUPAPISTE.ApplicationBulletinModel = function (params) {
  "use strict";
  var self = this;
  self.bulletinId = _.head(pageutil.getPagePath());
  self.vetumaService = params.vetumaService;
};
