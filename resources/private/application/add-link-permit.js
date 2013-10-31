LUPAPISTE.AddLinkPermitModel = function() {
  var self = this;
  self.dialogSelector = "#dialog-add-link-permit";

  self.appId = 0;
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

  // With these the initialization of self.selectedLinkPermit and self.kuntalupatunnus will work.
  // If it is done already in self.reset, it seems that Knockout will override
  // the initialized value when the new appMatches are received (for the select list in UI).
  self.tempLinkPermitIdForInit = "";
  self.tempLinkPermitTypeForInit = "";

  var getAppMatchesForLinkPermitsSelect = function(appId) {
    ajax.query("app-matches-for-link-permits", {id: appId})
      .processing(self.processing)
      .pending(self.pending)
      .success(function(data) {
        self.errorMessage(null);
        self.appMatches(data["app-links"]);

        if (self.tempLinkPermitIdForInit && self.tempLinkPermitTypeForInit) {
          if (self.tempLinkPermitTypeForInit === "lupapistetunnus") {
            self.selectedLinkPermit(self.tempLinkPermitIdForInit);
            self.kuntalupatunnus("");
          } else {
            self.selectedLinkPermit("");
            self.kuntalupatunnus(self.tempLinkPermitIdForInit);
          }
        } else {
          self.selectedLinkPermit("");
          self.kuntalupatunnus("");
        }
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.reset = function(app) {
    self.tempLinkPermitIdForInit = "";
    self.tempLinkPermitTypeForInit = "";

    var data = app.linkPermitData();
    if (_.size(data) && data.id && data.type) {
      self.tempLinkPermitIdForInit = data.id();
      self.tempLinkPermitTypeForInit = data.type();
    }
    self.appId = app.id();
    self.propertyId(app.propertyId());
    self.selectedLinkPermit("");
    self.kuntalupatunnus("");
    self.errorMessage(null);
    self.processing(false);
    self.pending(false);

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

  self.removeSelectedLinkPermit = function(appId) {
    ajax.command("remove-link-permit-by-app-id", {id: appId})
    .processing(self.processing)
    .pending(self.pending)
    .success(function(data) {
      self.errorMessage(null);
      self.tempLinkPermitIdForInit = "";
      self.tempLinkPermitTypeForInit = "";
      self.selectedLinkPermit("");
      self.kuntalupatunnus("");
      repository.load(appId);
    })
    .error(self.onError)
    .call();
  return false;
  };

  //Open the dialog

  self.openAddLinkPermitDialog = function(app) {
    self.reset(app);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

};
