LUPAPISTE.ApplicationBulletinModel = function(params) {
  "use strict";

  var self = this;

  self.bulletin = ko.observable();
  self.selectedTab = ko.observable("info");

  self.bulletinStateLoc = ko.pureComputed(function() {
    return ["bulletin", "state", self.bulletin().bulletinState];
  });
 
  self.map = gis
      .makeMap("bulletin-map", false)
      .updateSize()
      .center(404168, 6693765, 14);

  ajax.query("bulletin", {bulletinId: params.bulletinId})
    .success(function(res) {
      if (res.bulletin.id) {
        self.bulletin(res.bulletin);

        var location = self.bulletin().location;
        self.map.clear().updateSize().center(location[0], location[1]).add({x: location[0], y: location[1]});

        docgen.displayDocuments("#bulletinDocgen", self.bulletin(), self.bulletin().documents, {ok: function() { return false; }}, {disabled: true});
      } else {
        pageutil.openPage("bulletins");
      }
    })
    .call();

};
