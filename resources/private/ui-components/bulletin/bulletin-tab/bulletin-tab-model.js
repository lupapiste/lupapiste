LUPAPISTE.BulletinTabModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.appId = params.appId;
  self.appState = params.appState;
  self.authModel = params.authModel;
  self.bulletin = params.bulletinService.bulletin;
  self.comments = params.bulletinService.comments;

  self.showVersionComments = ko.observable(false);

  self.showVersions = ko.pureComputed(function() {
    return self.bulletin() && self.bulletin().versions.length > 0 && !self.showVersionComments();
  });

  self.showPublishing = ko.pureComputed(function() {
    return !self.showVersionComments();
  });

  ko.computed(function() {
    var id = self.appId();
    if(id) {
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: id});
    }
  });
};
