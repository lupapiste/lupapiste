LUPAPISTE.ChangeLocationModel = function() {
  var self = this;
  self.id = 0;
  self.dialogSelector = "";
  self.x = ko.observable(0);
  self.y = ko.observable(0);
  self.address = ko.observable("");
  self.propertyId = ko.observable("");

  self.reset = function(app) {
    self.id = app.id();
    self.dialogSelector = "#dialog-change-location-" + (app.infoRequest() ? "inforequest" : "application");
    self.x(app.location().x());
    self.y(app.location().y());
    self.address(app.address());
    self.propertyId(app.propertyId());
  };

  self.saveNewLocation = function() {
    ajax.command("change-location", {id: self.id,
                                     x: self.x(), y: self.y(),
                                     address: self.address(),
                                     propertyId: self.propertyId()})
    .success(function() {
      repository.load(self.id);
      LUPAPISTE.ModalDialog.close();
      })
    .call();
    return false;
  };

  self.changeLocation = function() {
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };
};
