LUPAPISTE.AddLinkPermitModel = function() {
  var self = this;
  self.dialogSelector = "#dialog-add-link-permit";

  self.appId = 0;
  self.tempLinkPermitDataForInit = [];
  self.propertyId = ko.observable("");
  self.kuntalupatunnus = ko.observable("");
  self.selectedLinkPermit = ko.observable("");
  self.appMatches = ko.observableArray([]);
  self.errorMessage = ko.observable(null);
  self.processing = ko.observable();
  self.pending = ko.observable();

  self.ok = ko.computed(function() {
    // XOR in javascript
    return (self.kuntalupatunnus() || self.selectedLinkPermit()) &&
           !(self.kuntalupatunnus() && self.selectedLinkPermit());
  });

  self.onError = function(resp) {
    self.errorMessage(resp.text);
  };

  var getAppMatchesForLinkPermitsSelect = function(appId) {
    ajax.query("app-matches-for-link-permits", {id: appId})
      .processing(self.processing)
      .pending(self.pending)
      .success(function(data) {

        data["app-links"] =
          _.reject(data["app-links"],
                   function(link){ return _.contains(self.tempLinkPermitDataForInit, link.id); });

        self.errorMessage(null);
        self.appMatches(data["app-links"]);
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.reset = function(app) {
    self.appId = app.id();
    self.propertyId(app.propertyId());
    self.selectedLinkPermit("");
    self.kuntalupatunnus("");
    self.errorMessage(null);
    self.processing(false);
    self.pending(false);

    self.tempLinkPermitDataForInit = [];

    var data = app.linkPermitData();
    if (_.size(data)) {
      self.tempLinkPermitDataForInit = _.reduce(data,
          function(memo, linkData){
        memo.push(linkData.id());
        return memo;
      }, []);
    }

    getAppMatchesForLinkPermitsSelect(app.id());
  };

  self.addLinkPermit = function() {
    var lupatunnus = self.selectedLinkPermit() || self.kuntalupatunnus();
    var data = {id: self.appId, linkPermitId: lupatunnus, propertyId: self.propertyId()};
    ajax.command("add-link-permit", data)
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        self.errorMessage(null);
        repository.load(self.appId);
        LUPAPISTE.ModalDialog.close();
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.removeSelectedLinkPermit = function(appId, linkPermitId) {
    ajax.command("remove-link-permit-by-app-id", {id: appId, linkPermitId: linkPermitId})
      .processing(self.processing)
      .pending(self.pending)
      .success(function(data) {
        self.errorMessage(null);
        self.selectedLinkPermit("");
        self.kuntalupatunnus("");
        repository.load(appId);
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.followAppLink = function(linkId) {
    window.location.hash = "#!/application/" + linkId;
    return false;
  };

  //Open the dialog

  self.openAddLinkPermitDialog = function(app) {
    self.reset(app);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

};
