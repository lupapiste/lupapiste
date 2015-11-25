LUPAPISTE.BulletinVersionsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.bulletin = params.bulletin;

  self.versions = ko.pureComputed(function() {
    return self.bulletin() ? self.bulletin().versions : [];
  });

  self.versionCommentsCount = function(version) {
    var versionCommentsLength = util.getIn(self, ["bulletin", "comments", version.id, "length"]);
    return versionCommentsLength ? versionCommentsLength + " " + loc("unit.kpl") : "";
  };

  self.showVersionComments = params.showVersionComments;
};
