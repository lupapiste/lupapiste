LUPAPISTE.ApplicationBulletinModel = function(params) {
  "use strict";
  var self = this;
  var bulletinService = params.bulletinService;
  var map = gis
      .makeMap("bulletin-map", false)
      .updateSize()
      .center(404168, 6693765, 14);

  self.bulletin = bulletinService.bulletin;
  self.selectedTab = ko.observable("info");

  self.bulletinStateLoc = ko.pureComputed(function() {
    return ["bulletin", "state", self.bulletin().bulletinState].join(".");
  });

  var id = self.bulletin.subscribe(function(bulletin) {
    if (util.getIn(self, ["bulletin", "id"])) {
      var location = bulletin.location;
      map.clear().updateSize().center(location[0], location[1]).add({x: location[0], y: location[1]});

      // This can be called only once
      docgen.displayDocuments("#bulletinDocgen", bulletin, bulletin.documents, {ok: function() { return false; }}, {disabled: true});
    }
  });

  self.dispose = function() {
    id.dispose();
  };

  hub.send("bulletinService::fetchBulletin", {id: params.bulletinId});
};
