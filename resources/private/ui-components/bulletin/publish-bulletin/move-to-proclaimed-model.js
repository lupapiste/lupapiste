LUPAPISTE.MoveToProclaimedModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.appId = params.appId;
  self.canPublish = params.canPublish;
  self.bulletinState = params.bulletinState;
  self.proclamationStartsAt = ko.observable();
  self.proclamationEndsAt   = ko.observable();
  self.proclamationText     = ko.observable();
  self.proclaimAgain        = ko.observable(false);

  self.proclamationStartsAt.extend({ beforeThan: self.proclamationEndsAt });
  self.proclamationEndsAt.extend({ afterThan: self.proclamationStartsAt });

  self.pending = ko.observable();

  self.all = ko.validatedObservable([self.proclamationStartsAt,
                                     self.proclamationEndsAt,
                                     self.proclamationText]);

  self.isValid = ko.pureComputed(function() {
    return self.all.isValid();
  });

  self.alreadyProclaimed = ko.pureComputed(function() {
    return ko.unwrap(self.bulletinState) === "proclaimed";
  });

  self.publishApplicationBulletin = function() {
    self.sendEvent("publishBulletinService", "moveToProclaimed", {
      id: self.appId(),
      proclamationEndsAt: self.proclamationEndsAt().getTime(),
      proclamationStartsAt: self.proclamationStartsAt().getTime(),
      proclamationText: self.proclamationText()
    });
  };

  self.addEventListener("publishBulletinService", "publishProcessing", function(event) {
    var state = event.state;
    self.pending(state === "pending");
  });

  self.addEventListener("publishBulletinService", "publishProcessed", function(event) {
    if (event.status === "success") {
      hub.send("indicator", {style: "positive"});
      self.proclaimAgain(false);
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: self.appId()});
    } else {
      hub.send("indicator", {style: "negative", message: event.text});
    }
  });
};
