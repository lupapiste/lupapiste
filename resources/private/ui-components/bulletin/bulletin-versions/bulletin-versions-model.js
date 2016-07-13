LUPAPISTE.BulletinVersionsModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.bulletin = params.bulletin;

  self.authModel = params.authModel;

  self.handleVersionClick = params.handleVersionClick;

  function mapVersions(v) {
    var model;
    switch (v.bulletinState) {
      case "proclaimed":
        model = new LUPAPISTE.EditableProclaimedBulletinModel(v, self.bulletin, self.authModel.ok("save-proclaimed-bulletin"));
        break;
      case "verdictGiven":
        model = new LUPAPISTE.EditableVerdictGivenBulletinModel(v, self.bulletin, self.authModel.ok("save-verdict-given-bulletin"));
        break;
      default:
        model = new LUPAPISTE.EditableBulletinModel(v, self.bulletin, false);
    }
    return model;
  }

  self.versions = ko.pureComputed(function() {
    return self.bulletin() ? _.map(self.bulletin().versions, mapVersions) : [];
  });

  self.editPublishedApplication = function(bulletin) {
    bulletin.edit(!bulletin.edit());
  };

  self.bulletinUrl = ko.pureComputed(function() { return "/app/" + loc.getCurrentLanguage() + "/bulletins#!/bulletin/" + util.getIn(self, ["bulletin", "id"]); });
};
