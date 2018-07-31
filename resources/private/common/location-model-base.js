/**
 * Base model for selecting application location
 */
LUPAPISTE.LocationModelBase = function(mapOptions) {
  "use strict";
  var self = this;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.processingAddress = ko.observable(false);
  self.locationServiceUnavailable = ko.observable(false);

  self.x = 0;
  self.y = 0;
  self.address = ko.observable("");
  self.propertyId = ko.observable("");
  self.municipalityCode = ko.observable("");
  self.refreshBuildings = ko.observable(true);

  self.reset = function() {
    return self.setXY(0,0).address("").propertyId("").municipalityCode("").refreshBuildings(true);
  };

  self.toJS = function() {
    return {
      x: self.x, y: self.y,
      address: self.address(),
      propertyId: util.prop.toDbFormat(self.propertyId()),
      propertyIdSource: self.locationServiceUnavailable() ? "user" : "location-service",
      refreshBuildings: self.refreshBuildings()
    };
  };

  self.municipalityName = ko.pureComputed(function() {
    return self.municipalityCode() ? loc(["municipality", self.municipalityCode()]): "";
  });

  self.propertyIdHumanReadable = ko.pureComputed(function() {
    return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
  });

  self.propertyIdOk = ko.pureComputed(function() {
    return !_.isBlank(self.propertyId()) && util.prop.isPropertyId(self.propertyId());
  });

  self.addressOk = ko.pureComputed(function() { return self.municipalityCode() && !_.isBlank(self.address()); });

  self.propertyIdValidated = ko.observable(true);


  self.setAddress = function(a) {
    var newAddress = "";
    if (a) {
      newAddress = a.street;
      if (a.number && a.number !== "0") {
        newAddress = newAddress + " " + a.number;
      }
    }
    return self.address(newAddress);
  };

  //
  // Map and coordinate handling
  //

  self._map = null;

  self.map = function() {
    if (!self._map) {
      self._map = gis
        .makeMap(mapOptions.mapId, {zoomWheelEnabled: mapOptions.zoomWheelEnabled})
        .center(404168, 7205000, mapOptions.initialZoom)
        .addClickHandler(function(x, y) {
          self.reset().setXY(x, y)
            .beginUpdateRequest().searchPropertyInfo(x, y).searchAddress(x, y);
          if (_.isFunction(mapOptions.afterClick)) {mapOptions.afterClick();}
        });

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

  //
  // Concurrency control
  //

  self.requestContext = new RequestContext();
  self.beginUpdateRequest = function() {
    self.requestContext.begin();
    return self;
  };

  //
  // Search
  //

  self.onError = function(e) {
    if (e.status === 404) {
      hub.send("indicator", {style: "negative", message: "integration.addressNotFound"});
    } else if (e.status > 400) {
      hub.send("indicator", {style: "negative", message: "integration.getAddressNotWorking", html: true});
    }
  };

  self.searchPropertyInfo = function(x, y) {
    if (x && y) {
      locationSearch.propertyInfoByPoint(self.requestContext, x, y, function(resp) {
        self.locationServiceUnavailable(false);
        self.propertyId(resp.propertyId);
        self.municipalityCode(resp.municipality);
        self.propertyIdValidated(true);
      }, function(err) {
        self.locationServiceUnavailable(err.status === 503);
        self.onError(err);
      }, self.processing);
    }
    return self;
  };

  self.searchPropertyId = function(x, y) {
    if (x && y) {
      locationSearch.propertyIdByPoint(self.requestContext, x, y, function(id) {
        self.locationServiceUnavailable(false);
        self.propertyId(id);
        self.propertyIdValidated(true);
      }, function(err) {
        self.locationServiceUnavailable(err.status === 503);
        self.onError(err);
      }, self.processing);
    }
    return self;
  };

  self.searchAddress = function(x, y) {
    if (x && y) {
      locationSearch.addressByPoint(self.requestContext, x, y, self.setAddress, self.onError, self.processingAddress);
    }
    return self;
  };

};
