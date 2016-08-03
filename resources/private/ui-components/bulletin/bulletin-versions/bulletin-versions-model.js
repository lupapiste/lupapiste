LUPAPISTE.BulletinVersionsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.params = params;
  self.open = ko.observable( true );
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

  self.versions = self.disposedPureComputed(function() {
    return self.bulletin() ? _.map(self.bulletin().versions, mapVersions) : [];
  });

  self.editPublishedApplication = function(bulletin) {
    bulletin.edit(!bulletin.edit());
  };

  self.bulletinUrl = self.disposedPureComputed(function() { return "/app/" + loc.getCurrentLanguage() + "/bulletins#!/bulletin/" + util.getIn(self, ["bulletin", "id"]); });
};
