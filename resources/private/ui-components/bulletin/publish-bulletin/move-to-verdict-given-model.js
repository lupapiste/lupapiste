LUPAPISTE.MoveToVerdictGivenModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.appId = params.appId;
  self.canPublish = params.canPublish;
  self.bulletinState = params.bulletinState;

  self.verdictGivenAt       = ko.observable();
  self.appealPeriodStartsAt = ko.observable();
  self.appealPeriodEndsAt   = ko.observable();
  self.verdictGivenText     = ko.observable();

  self.verdictGivenAt.extend({ beforeThan: { params: self.appealPeriodStartsAt, message: loc("bulletins.beforeThan.appealPeriodStartsAt") } });
  self.appealPeriodStartsAt.extend({ beforeThan: self.appealPeriodEndsAt });
  self.appealPeriodEndsAt.extend({ afterThan: self.appealPeriodStartsAt });

  self.pending = ko.observable();

  self.all = ko.validatedObservable([self.verdictGivenAt,
                                     self.appealPeriodStartsAt,
                                     self.appealPeriodEndsAt,
                                     self.verdictGivenText]);

  self.isValid = ko.pureComputed(function() {
    return self.all.isValid();
  });

  self.publishApplicationBulletin = function() {
    self.sendEvent("publishBulletinService", "moveToVerdictGiven", {
      id: self.appId(),
      verdictGivenAt:       self.verdictGivenAt().getTime(),
      appealPeriodStartsAt: self.appealPeriodStartsAt().getTime(),
      appealPeriodEndsAt:   self.appealPeriodEndsAt().getTime(),
      verdictGivenText:     self.verdictGivenText()
    });
  };

  self.addEventListener("publishBulletinService", "publishProcessing", function(event) {
    var state = event.state;
    self.pending(state === "pending");
  });

  self.addEventListener("publishBulletinService", "publishProcessed", function(event) {
    if (event.status === "success") {
      hub.send("indicator", {style: "positive"});
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: self.appId()});
    } else {
      hub.send("indicator", {style: "negative", message: event.text});
    }
  });
};
