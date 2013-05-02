LUPAPISTE.ChangeLocationModel = function() {
  var self = this;
  self.id = 0;
  self.dialogSelector = "";
  self.x = ko.observable(0);
  self.y = ko.observable(0);
  self.address = ko.observable("");
  self.propertyId = ko.observable("");
  self.errorMessage = ko.observable(null);

  self.reset = function(app) {
    self.id = app.id();
    self.dialogSelector = "#dialog-change-location-" + (app.infoRequest() ? "inforequest" : "application");
    self.x(app.location().x());
    self.y(app.location().y());
    self.address(app.address());
    self.propertyId(app.propertyId());
    self.errorMessage(null);
  };

  self.onSuccess = function() {
    self.errorMessage(null);
    repository.load(self.id);
    LUPAPISTE.ModalDialog.close();
  };

  self.onError = function(resp) {
    self.errorMessage(resp.text);
  };

  self.saveNewLocation = function() {
    var data = {id: self.id, x: self.x(), y: self.y(), address: self.address(), propertyId: self.propertyId()};
    ajax.command("change-location", data).success(self.onSuccess).error(self.onError).call();
    return false;
  };

  self.changeLocation = function() {
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };
};
