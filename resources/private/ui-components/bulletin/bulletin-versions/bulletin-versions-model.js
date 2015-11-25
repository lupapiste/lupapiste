LUPAPISTE.BulletinVersionsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.bulletin = params.bulletin;

  self.versions = ko.pureComputed(function() {
    return self.bulletin() ? self.bulletin().versions : [];
  });

  ko.computed(function() {
    var id = self.params.appId();
    if(id) {
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: id});
    }
  });

  self.versionCommentsCount = function(version) {
    var versionCommentsLength = util.getIn(self, ["bulletin", "comments", version.id, "length"]);
    return versionCommentsLength ? versionCommentsLength + " " + loc("unit.kpl") : "";
  };
};
