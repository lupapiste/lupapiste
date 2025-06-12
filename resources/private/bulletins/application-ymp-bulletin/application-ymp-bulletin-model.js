LUPAPISTE.ApplicationYmpBulletinModel = function(params) {
  "use strict";
  var self = this;
  var bulletinService = params.bulletinService;
  var map = gis
      .makeMap("bulletin-map", {zoomWheelEnabled: false})
      .updateSize()
      .center(404168, 6693765, 14);

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  self.bulletin = bulletinService.bulletin;
  self.userInfo = params.userInfo;
  self.fileuploadService = params.fileuploadService;

  self.bulletinId = params.bulletinId;
  self.versionId  = ko.observable();
  self.proclamationEndsAt = ko.observable();

  self.authenticated = params.vetumaService.authenticated;

  self.showCljsDocgen = ko.observable(features.cljsDocgenInitialState());

  self.selectedTab = ko.observable().extend({
    limited: {values: ["verdicts", "info", "attachments", "instructions"], defaultValue: "instructions"}
  });

  var stateToTab = {
    "final": "verdicts",
    "proclaimed":"instructions",
    "verdictGiven":"instructions"
  };

  hub.subscribe("toggle-docgen-cljs",
    function (e) {
      if (e.enabled) {
        docgen.clear("bulletinDocgen");
      }

      self.showCljsDocgen(e.enabled);

      // Same as self.bulletin subscribe
      init(self.bulletin());
    });

  self.disposedComputed(function() {
    self.selectedTab(_.nth(params.pagePath(), 1)
                     || _.get(stateToTab, util.getIn(self, ["bulletin", "bulletinState"])));
  });

  self.attachments = self.disposedPureComputed(function() {
    return util.getIn(self, ["bulletin", "attachments"], []);
  });

  self.verdicts = self.disposedPureComputed(function() {
    return util.verdictsWithTasks(ko.mapping.toJS(self.bulletin));
  });

  self.bulletinStateLoc = self.disposedPureComputed(function() {
    return ["bulletin", "state", self.bulletin().bulletinState].join(".");
  });

  self.inProclaimedState = self.disposedPureComputed(function() {
    return _.includes(["proclaimed"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.inVerdictGivenState = self.disposedPureComputed(function() {
    return _.includes(["verdictGiven"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.inFinalState = self.disposedPureComputed(function() {
    return "final" === util.getIn(self, ["bulletin", "bulletinState"]);
  });

  self.currentStateInSeq = self.disposedPureComputed(function() {
    return _.includes(self.bulletin().stateSeq, self.bulletin().bulletinState);
  });

  self.showVerdictsTab = self.disposedPureComputed(function() {
    return _.includes(["verdictGiven", "final"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.showInstructionsTab = self.disposedPureComputed(function() {
    return _.includes(["proclaimed", "verdictGiven"], util.getIn(self, ["bulletin", "bulletinState"]));
  });

  self.showInfoTab = self.disposedPureComputed(function() {
    return util.getIn(self, ["bulletin", "bulletinState"]) === "proclaimed";
  });

  self.showAttachmentsTab = self.disposedPureComputed(function() {
    return util.getIn(self, ["bulletin", "bulletinState"]) === "proclaimed";
  });

  function init(bulletin) {
    if (util.getIn(self, ["bulletin", "id"])) {
      var location = bulletin.location;
      self.versionId(bulletin.versionId);
      self.proclamationEndsAt(bulletin.proclamationEndsAt);
      map.clear().updateSize().center(location[0], location[1]).add({x: location[0], y: location[1]});

      if (self.showCljsDocgen()) {
        // There's an unfortunate race condition between the JS code and the CLJS router.
        // Both use the jQuery `.ready()` mechanism. Sometimes (at least in local environment)
        // the bulletin might be fetched before the router / `re-frame` has updated the route
        // and executed any related initialization effects. In this case they register this hub subscription.
        setTimeout(function (){
          // Notify the new CLJS Docgen implementation about the application
          hub.send("cljs-app-loaded",
            {app: bulletin,
              applicationDetails: {application: bulletin}});
        }, 500);
      } else {
        // This can be called only once
        docgen.displayDocuments("bulletinDocgen", bulletin, bulletin.documents, {disabled: true, authorizationModel: params.auth});
      }
    }
  }

  var id = self.bulletin.subscribe(function(bulletin) {
    init(bulletin);
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

  self.commentDeadline = self.disposedPureComputed(function() {
    return util.getIn(self, ["bulletin", "commentDeadline"]);
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
