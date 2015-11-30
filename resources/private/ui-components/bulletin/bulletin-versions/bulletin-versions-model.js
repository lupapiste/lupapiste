LUPAPISTE.BulletinVersionsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.bulletin = params.bulletin;

  self.authModel = params.authModel;

  self.latestVersion = ko.observable({});

  function mapVersions(v) {
    var model;
    switch (v.bulletinState) {
      case "proclaimed":
        model = new LUPAPISTE.EditableProclaimedBulletinModel(v, self.bulletin().id);
        if (ko.unwrap(model.id) === util.getIn(self, ["latestVersion", "id"])) {
          model.editable(true);
        }
        break;
      case "verdictGiven":
        model = new LUPAPISTE.EditableVerdictGivenBulletinModel(v, self.bulletin().id);
        if (ko.unwrap(model.id) === util.getIn(self, ["latestVersion", "id"])) {
          model.editable(true);
        }
        break;
      default:
        model = new LUPAPISTE.EditableBulletinModel(v, self.bulletin().id);
    }
    return model;
  }

  self.versions = ko.pureComputed(function() {
    self.latestVersion(_.last(self.bulletin().versions));
    return self.bulletin() ? _.map(self.bulletin().versions, mapVersions) : [];
  });

  self.showVersionComments = params.showVersionComments;

  self.editPublishedApplication = function(bulletin) {
    bulletin.edit(!bulletin.edit());
  }
};
