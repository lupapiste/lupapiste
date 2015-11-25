LUPAPISTE.BulletinTabModel = function(params) {
  "use strict";
  var self = this;

  self.appId = params.appId;
  self.appState = params.appState;
  self.authModel = params.authModel;

  self.bulletin = params.bulletinService.bulletin;

};
