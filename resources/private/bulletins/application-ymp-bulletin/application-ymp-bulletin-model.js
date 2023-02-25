LUPAPISTE.ApplicationYmpBulletinModel = function(params) {
  "use strict";
  var self = this;
  var bulletinService = params.bulletinService;
  var map = gis
      .makeMap("bulletin-map", {zoomWheelEnabled: false})
      .updateSize()
      .center(404168, 6693765, 14);

  self.bulletin = bulletinService.bulletin;
  self.userInfo = params.userInfo;
  self.fileuploadService = params.fileuploadService;

  self.bulletinId = params.bulletinId;
  self.versionId  = ko.observable();
  self.proclamationEndsAt = ko.observable();

  self.authenticated = params.authenticated;

  self.selectedTab = ko.observable().extend({
    limited: {values: ["verdicts", "info", "attachments", "instructions"], defaultValue: "instructions"}
  });

  var stateToTab = {
    "final": "verdicts",
    "proclaimed":"instructions",
    "verdictGiven":"instructions"
  };

  ko.computed(function() {
    self.selectedTab(_.nth(params.pagePath(), 1) || _.get(stateToTab, util.getIn(self, ["bulletin", "bulletinState"])));
  });

  self.authenticated = params.authenticated;

  self.attachments = ko.pureComputed(function() {
    return util.getIn(self, ["bulletin", "attachments"], []);
  });

  self.verdicts = ko.pureComputed(function() {
    return util.verdictsWithTasks(ko.mapping.toJS(self.bulletin));
  });

  self.bulletinStateLoc = ko.pureComputed(function() {
    return ["bulletin", "state", self.bulletin().bulletinState].join(".");
  });

  self.inProclaimedState = ko.pureComputed(function() {
    return _.includes(["proclaimed"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.inVerdictGivenState = ko.pureComputed(function() {
    return _.includes(["verdictGiven"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.inFinalState = ko.pureComputed(function() {
    return "final" === util.getIn(self, ["bulletin", "bulletinState"]);
  });

  self.currentStateInSeq = ko.pureComputed(function() {
    return _.includes(self.bulletin().stateSeq, self.bulletin().bulletinState);
  });

  self.showVerdictsTab = ko.pureComputed(function() {
    return _.includes(["verdictGiven", "final"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.showInstructionsTab = ko.pureComputed(function() {
    return _.includes(["proclaimed", "verdictGiven"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.showInfoTab = ko.pureComputed(function() {
    return util.getIn(self, ["bulletin", "bulletinState"]) === "proclaimed";
  });

  self.showAttachmentsTab = ko.pureComputed(function() {
    return util.getIn(self, ["bulletin", "bulletinState"]) === "proclaimed";
  });

  self.showCommenting = ko.pureComputed(function() {
    return self.canCommentCurrentBulletin();
  });

  var id = self.bulletin.subscribe(function(bulletin) {
    if (util.getIn(self, ["bulletin", "id"])) {
      var location = bulletin.location;
      self.versionId(bulletin.versionId);
      self.proclamationEndsAt(bulletin.proclamationEndsAt);
      map.clear().updateSize().center(location[0], location[1]).add({x: location[0], y: location[1]});
      // This can be called only once
      docgen.displayDocuments("bulletinDocgen", bulletin, bulletin.documents, {disabled: true, authorizationModel: params.auth});
    }
  });

  self.clickAuthenticationButton = function() {
    $("#vetuma-init")[0].click();
  };

  self.openTab = function(tab) {
    pageutil.openPage("ymp-bulletin", [self.bulletinId(), tab]);
  };

  self.scrollToCommenting = function() {
    $("#bulletin-comment")[0].scrollIntoView(true);
  };


  self.exportToPdf = function() {
    window.open("/api/raw/bulletin-pdf-export?bulletinId=" + self.bulletinId() + "&lang=" + loc.currentLanguage, "_blank");
  };

  self.canCommentCurrentBulletin = ko.pureComputed(function() {
    return util.getIn(self, ["bulletin", "canComment"]);
  });

  hub.send("bulletinService::fetchBulletin", {id: self.bulletinId()});

  var returnUrl = "/app/" + loc.getCurrentLanguage() + "/bulletins#!/ymp-bulletin/" + self.bulletinId();
  self.vetumaParams = {success: returnUrl,
                       cancel:  returnUrl + "/cancel",
                       error:   returnUrl + "/error",
                       y:       returnUrl,
                       vtj:     returnUrl,
                       id:      "vetuma-init"};

  self.dispose = function() {
    id.dispose();
  };

};
