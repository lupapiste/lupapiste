LUPAPISTE.AddPropertyDialogModel = function() {
  "use strict";
  var self = this;

  var app = lupapisteApp.models.application;

  self.x = app.location().x();
  self.y = app.location().y();
  self.propertyId = ko.observable(app.propertyId());

  self.selectedNotAppProperty = ko.pureComputed(function() {
    return self.propertyId() !== app.propertyId();
  });

  function resolvePropertyId(x, y) {
    ajax
      .get("/proxy/property-id-by-point")
      .param("x", x)
      .param("y", y)
      .success(function(propertyId) {
        self.propertyId(propertyId);
      })
      .fail(_.noop)
      .call();
  }

  function moveMarkerAndCenterMap(x, y) {
    self.map().clear().add({x: x, y: y});
    self.map().center(x, y, 14);
  }

  self._map = null;
  self.map = function() {
    if (!self._map) {
      self._map = gis
        .makeMap("add-location-map", {zoomWheelEnabled: false})
        .addClickHandler(function(x, y) {
          self.x = x;
          self.y = y;
          resolvePropertyId(x, y);
          moveMarkerAndCenterMap(x, y);

          return false;
        });
    }
    return self._map;
  };

  self.submit = function() {
    var updates = [["kiinteisto.kiinteistoTunnus", self.propertyId()]];
    var id = app.id(); // app is disposed after dialog is closed
    ajax
      .command("create-doc", { id: id,
                               schemaName: "secondary-kiinteistot",
                               updates: updates,
                               fetchRakennuspaikka: true })
      .success(function() { repository.load(id); })
      .call();
    hub.send("close-dialog");
  };

  moveMarkerAndCenterMap(self.x, self.y);

  self.dispose = function() {
    self.selectedNotAppProperty.dispose();
    self.selectedNotAppProperty = null;
    if (self._map) {
      self._map.destroy();
      self._map = null;
    }
    app = null;
  };
};
