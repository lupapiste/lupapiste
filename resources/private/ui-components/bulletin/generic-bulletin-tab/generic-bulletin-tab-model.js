LUPAPISTE.GenericBulletinTabModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.appId = params.appId;
  self.appState = params.appState;
  self.authModel = params.authModel;

  self.disposedComputed(function() {
    var id = self.appId();
  });
};
