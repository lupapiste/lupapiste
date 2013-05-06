LUPAPISTE.ChangeLocationModel = function() {
  var self = this;

  self.map = {};
  self.id = 0;
  self.municipalityCode = 0;
  self.dialogSelector = "#dialog-change-location";
  self.x = 0;
  self.y = 0;
  self.address = ko.observable("");
  self.propertyId = ko.observable("");
  self.errorMessage = ko.observable(null);

  self.propertyId.subscribe(function(id) {
    var human = util.prop.toHumanFormat(id);
    if (human != id) {
      self.propertyId(human);
    }
  });

  self.ok = ko.computed(function() {
    return util.prop.isPropertyId(self.propertyId()) && self.address();
  });

  self.reset = function(app) {
    self.id = app.id();
    self.x = app.location().x();
    self.y = app.location().y();
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
    var data = {id: self.id, x: self.x, y: self.y, address: self.address(), propertyId: self.propertyId()};
    ajax.command("change-location", data).success(self.onSuccess).error(self.onError).call();
    return false;
  };

  self.changeLocation = function(app) {
    self.reset(app);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
    self.map.updateSize();
    self.map.clear().center(self.x, self.y, 10).add(self.x, self.y);
  };

  $(function() {
    self.map = gis.makeMap("change-location-map").center([{x: 404168, y: 6693765}], 10);
  });
};
