LUPAPISTE.AddPropertyDialogModel = function(params) {
  "use strict";
  var self = this;

  var app = lupapisteApp.models.application;
  var humanize = util.prop.toHumanFormat;

  self.x = app.location().x();
  self.y = app.location().y();
  self.propertyId = ko.observable(humanize(app.propertyId()));

  function resolvePropertyId(x, y) {
    ajax
      .get("/proxy/property-id-by-point")
      .param("x", x)
      .param("y", y)
      .success(function(propertyId) {
        self.propertyId(humanize(propertyId));
      })
      .fail(_.noop)
      .call();
  }

  function moveMarkerAndCenterMap(x, y) {
    self.map().clear().add({x: x, y: y});
    self.map().center(x, y, 14);
  }

  var _map = null;
  self.map = function() {
    if (!_map) {
      _map = gis
        .makeMap("add-location-map", false)
        .addClickHandler(function(x, y) {
          self.x = x;
          self.y = y;
          resolvePropertyId(x, y);
          moveMarkerAndCenterMap(x, y);

          return false;
        });
    }
    return _map;
  };

  self.submit = function() {
    // AJAX new propertyId here
    hub.send("close-dialog");
  };

  moveMarkerAndCenterMap(self.x, self.y);
};