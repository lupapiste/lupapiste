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

  self.showHelp = ko.pureComputed(function() {
    // Only show the help element if there's some text to show and if the user is an authority
    return self.helpText.length > 0 && self.authModel.ok("application-authorities");
  });

  self.notYetPublishedForApplicant = ko.pureComputed(function() {
    return !self.bulletinState() && lupapisteApp.models.currentUser.isApplicant();
  });

  self.canMoveToProclaimed = ko.pureComputed(function() {
    return self.authModel.ok("move-to-proclaimed");
  });

  self.canMoveToVerdictGiven = ko.pureComputed(function() {
    // TODO bulletin state check in backend
    return self.authModel.ok("move-to-verdict-given");
  });

  self.canMoveToFinal = ko.pureComputed(function() {
    return self.authModel.ok("move-to-final");
  });

  self.showBulletinState = ko.pureComputed(function() {
    return self.bulletinState() ? "bulletin.state." + self.bulletinState() : undefined;
  });

  self.canNotPublishForAuthority = ko.pureComputed(function() {
    return !self.canMoveToProclaimed() && !self.canMoveToVerdictGiven() && !self.canMoveToFinal();
  });

  self.addHubListener({eventType:"publishBulletinService::publishProcessed", status: "success"}, function() {
    self.authModel.refresh(self.appId());
  });
};
