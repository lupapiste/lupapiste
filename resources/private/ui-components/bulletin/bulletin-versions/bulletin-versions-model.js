LUPAPISTE.BulletinVersionsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.bulletin = params.bulletin;

  self.versions = self.bulletin() ? self.bulletin().versions : [];

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  ko.computed(function() {
    var id = self.params.appId();
    if(id) {
      self.sendEvent("publishBulletinService", "fetchBulletinVersions", {bulletinId: id});
    }
  });

  self.versionCommentsCount = function(version) {
    var versionComments = self.bulletin().comments[version.id];
    return versionComments ? versionComments.length + " " + loc("unit.kpl") : "";
  };
};
