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
    if (self.appState() === "sent") {
      return "help.bulletin.state.proclaimed";
    } else if (self.appState() === "verdictGiven" && self.bulletinState() === "proclaimed") {
      return "help.bulletin.state.verdictGiven";
    } else if (self.appState() === "verdictGiven" && self.bulletinState() === "verdictGiven") {
      return "help.bulletin.state.final";
    }
  });

  self.canPublish = ko.pureComputed(function() {
    return self.authModel.ok("publish-bulletin");
  });

  self.canMoveToProclaimed = ko.pureComputed(function() {
    return self.authModel.ok("move-to-proclaimed");
  });

  self.canMoveToVerdictGiven = ko.pureComputed(function() {
    // TODO bulletin state check in backend
    return self.authModel.ok("move-to-verdict-given") && self.bulletinState() === "proclaimed";
  });

  self.canMoveToFinal = ko.pureComputed(function() {
    return self.authModel.ok("move-to-final") && self.bulletinState() === "verdictGiven";
  });

  self.isInFinalState = ko.pureComputed(function() {
    return self.bulletinState() === "final";
  });

  ko.computed(function() {
    var id = self.appId();
    if(id) {
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: id});
    }
  });
};
