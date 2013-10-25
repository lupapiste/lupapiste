LUPAPISTE.AddLinkPermitModel = function() {
  var self = this;
  self.dialogSelector = "#add-link-permit";

  self.appId = 0;
  self.kuntalupatunnus = ko.observable("");
  self.chosenLinkPermit = ko.observable("");
  self.errorMessage = ko.observable(null);
  self.processing = ko.observable();
  self.pending = ko.observable();

  self.ok = ko.computed(function() {
    // XOR in javascript
    return (self.kuntalupatunnus() || self.chosenLinkPermit()) &&
          !(self.kuntalupatunnus() && self.chosenLinkPermit());
  });

  self.reset = function(app) {
    self.appId = app.id();
    self.kuntalupatunnus("");
//    self.kuntalupatunnus(app.kuntalupatunnus());  // ??
    self.chosenLinkPermit("");
//    self.chosenLinkPermit(app.chosenLinkPermit());  // ??
    self.errorMessage(null);
    self.processing(false);
    self.pending(false);
  };


  //
  // Concurrency control    *** TODO: Onko talle tarvetta? ***
  //

//  self.requestContext = new RequestContext();
//  self.beginUpdateRequest = function() {
//    self.errorMessage(null);
//    self.requestContext.begin();
//    return self;
//  };


  self.setChosenLinkPermit = function(linkPermit) {
//    self.beginUpdateRequest().chosenLinkPermit(linkPermit);
    self.chosenLinkPermit(linkPermit);
  };


  //Saving

  self.onSuccess = function() {
    self.errorMessage(null);
    repository.load(self.appId);   // TODO: Mihin tata tarvitaan?
    LUPAPISTE.ModalDialog.close();
  };

  self.onError = function(resp) {
    self.errorMessage(resp.text);
  };

  self.addLinkPermit = function() {
    // *** TODO: Miten lupatunnus selvitetään? ***
    var lupatunnus = self.chosenLinkPermit() || self.kuntalupatunnus();  // ** TODO: Korjaa tama! **
    console.log("addLinkPermit, lupatunnus: ", lupatunnus);

    var data = {appId: self.appId, linkPermit: lupatunnus};
    ajax.command("add-link-permit", data)
      .processing(self.processing)
      .pending(self.pending)
      .success(self.onSuccess)
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
