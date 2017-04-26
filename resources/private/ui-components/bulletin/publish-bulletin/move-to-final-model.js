LUPAPISTE.MoveToFinalModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.appId = params.appId;
  self.canPublish = params.canPublish;
  self.bulletinState = params.bulletinState;

  self.officialAt = ko.observable();

  self.pending = ko.observable();

  self.all = ko.validatedObservable([self.officialAt]);

  self.isValid = ko.pureComputed(function() {
    return self.all.isValid();
  });

  self.publishApplicationBulletin = function() {
    self.sendEvent("publishBulletinService", "moveToFinal", {
      id: self.appId(),
      officialAt: self.officialAt().getTime()
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
