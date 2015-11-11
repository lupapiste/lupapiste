LUPAPISTE.ApplicationBulletinModel = function(params) {
  "use strict";
  var self = this;
  var bulletinService = params.bulletinService;
  var map = gis
      .makeMap("bulletin-map", false)
      .updateSize()
      .center(404168, 6693765, 14);

  self.bulletin = bulletinService.bulletin;

  self.bulletinId = params.bulletinId;
  self.versionId  = ko.observable();
  self.selectedTab = ko.observable("info");

  self.authenticated = params.authenticated || ko.observable(false);

  self.bulletinStateLoc = ko.pureComputed(function() {
    return ["bulletin", "state", self.bulletin().bulletinState].join(".");
  });
  self.currentStateInSeq = ko.pureComputed(function() {
    return _.contains(self.bulletin().stateSeq, self.bulletin().bulletinState);
  });

  var id = self.bulletin.subscribe(function(bulletin) {
    if (util.getIn(self, ["bulletin", "id"])) {
      var location = bulletin.location;
      self.versionId(bulletin.versionId);
      map.clear().updateSize().center(location[0], location[1]).add({x: location[0], y: location[1]});
      // This can be called only once
      docgen.displayDocuments("#bulletinDocgen", bulletin, bulletin.documents, {ok: function() { return false; }}, {disabled: true});
    }
  });

  self.dispose = function() {
    id.dispose();
  };

  hub.send("bulletinService::fetchBulletin", {id: self.bulletinId});

  var returnUrl = "/app/" + loc.getCurrentLanguage() + "/bulletins#!/bulletin/" + self.bulletinId;
  self.vetumaParams = {success: returnUrl,
                       cancel:  returnUrl,
                       error:   returnUrl,
                       y:       returnUrl,
                       vtj:     returnUrl,
                       id:      "vetuma-init"};
};
