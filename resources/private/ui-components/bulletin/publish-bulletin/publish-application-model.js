LUPAPISTE.PublishApplicationModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.authModel = params.authModel;
  self.appId     = params.appId;
  self.appState  = params.appState;
  self.bulletin  = params.bulletin;

  self.processing = ko.observable();

  self.bulletinState = ko.pureComputed(function() {
    return util.getIn(self, ["bulletin", "bulletinState"]);
  });

  self.helpText = ko.pureComputed(function() {
    return "help.bulletin.application.state." + self.appState();
  });

  self.canPublish = ko.pureComputed(function() {
    return self.authModel.ok("publish-bulletin");
  });

  self.canMoveToProclaimed = ko.pureComputed(function() {
    return self.authModel.ok("move-to-proclaimed");
  });

  ko.computed(function() {
    var id = self.appId();
    if(id) {
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: id});
    }
  });
};
