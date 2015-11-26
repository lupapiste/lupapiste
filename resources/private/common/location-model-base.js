/**
 * Base model for selecting application location
 */
LUPAPISTE.LocationModelBase = function(mapOptions) {
  "use strict";
  var self = this;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);

  self.x = 0;
  self.y = 0;
  self.address = ko.observable("");
  self.propertyId = ko.observable("");

  self.toJS = function() {
    return {
      x: self.x, y: self.y,
      address: self.address(),
      propertyId: util.prop.toDbFormat(self.propertyId())
    };
  };

  self.propertyIdHumanReadable = ko.pureComputed(function() {
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
  });
  self.propertyIdOk = ko.pureComputed(function() {
    return !_.isBlank(self.propertyId()) && util.prop.isPropertyId(self.propertyId());
  });

  self._map = null;

  self.map = function() {
    if (!self._map) {
      self._map = gis
        .makeMap(mapOptions.mapId, mapOptions.zoomWheelEnabled)
        .center(404168, 7205000, mapOptions.initialZoom)
        .addClickHandler(mapOptions.clickHandler);

      if (mapOptions.popupContentModel) {
        self._map.setPopupContentModel(self, mapOptions.popupContentModel);
      }
    }
    return self._map;
  };

  self.clearMap = function() {
    self.map().clear().updateSize();
    return self;
  };

  self.hasXY = function() {
    return self.x !== 0 && self.y !== 0;
  };

  self.drawLocation = function() {
    if (self.hasXY()) {
      self.map().clear().add({x: self.x, y: self.y}, true);
    } else {
      self.clearMap();
    }
    return self;
  };

  self.setXY = function(x, y) {
    self.x = x;
    self.y = y;
    return self.drawLocation();
  };

  self.center = function(zoom, x, y) {
    self.map().center(x || self.x, y || self.y, zoom);
    return self;
  };

  return self;
};
