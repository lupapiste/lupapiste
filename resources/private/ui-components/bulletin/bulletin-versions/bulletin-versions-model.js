LUPAPISTE.BulletinVersionsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.bulletin = params.bulletin;

  self.authModel = params.authModel;

  self.latestVersion = ko.observable({});

  function mapVersions(v) {
    var model = new LUPAPISTE.EditableBulletinModel(v, self.bulletin().id);
    if (model.id() === util.getIn(self, ["latestVersion", "id"])) {
      model.editable(true);
    }
    return model;
  }

  self.versions = ko.pureComputed(function() {
    self.latestVersion(_.last(self.bulletin().versions));
    return self.bulletin() ? _.map(self.bulletin().versions, mapVersions) : [];
  });

  self.showVersionComments = params.showVersionComments;

  self.versionCommentsCount = ko.pureComputed(function() {
    // var versionCommentsLength = util.getIn(self, ["bulletin", "comments", version.id, "length"]);
    // return versionCommentsLength ? versionCommentsLength + " " + loc("unit.kpl") : "";
    return "TODO";
  });

  self.editPublishedApplication = function(bulletin) {
    bulletin.edit(!bulletin.edit());
  }
};
