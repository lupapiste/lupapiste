LUPAPISTE.PublishApplicationModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.authModel = params.authModel;

  self.appId = params.appId;

  self.appState = params.appState;

  self.bulletin = params.bulletin;

  self.processing = ko.observable();

  self.helpText = ko.pureComputed(function() {
    return "help.bulletin.application.state." + self.appState();
  });

  self.canPublish = ko.pureComputed(function() {
    return self.authModel.ok("publish-bulletin");
  });

  self.canProclaim = ko.pureComputed(function() {
    // TODO authModel
    return self.appState() === "sent";
  });

  self.publishApplicationBulletin = function() {
    self.sendEvent("publishBulletinService", "publishBulletin", {id: self.appId()});
  };

  ko.computed(function() {
    var id = self.appId();
    if(id) {
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: id});
    }
  });

  self.bulletin.subscribe(function(val) {
    console.log("uutta", val);
  });
};
