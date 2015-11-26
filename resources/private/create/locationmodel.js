LUPAPISTE.LocationModel = function() {
  "use strict";
  var self = this;

  self.processing = ko.observable(false);

  self.x = ko.observable(0);
  self.y = ko.observable(0);
  self.address = ko.observable("");

  self.propertyId = ko.observable("");
  self.propertyIdHumanReadable = ko.pureComputed(function() {
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
    });

  self.municipalityCode = ko.observable("");

  self.municipalityName = ko.pureComputed(function() {
    return self.municipalityCode() ? loc(["municipality", self.municipalityCode()]): "";
  });

  self.reset = function() {
    self.x(0).y(0).address("").propertyId("").municipalityCode("");
  };

  self.setAddress = function(a) {
    return self.municipalityCode(a.municipality).address(a ? a.street + " " + a.number : "");
  };

  self.setPropertyId = function(id) {
    var currentMuni = self.municipalityCode();
    self.propertyId(id);
    if (!currentMuni || !_.startsWith(id, currentMuni)) {
      ajax.query("municipality-by-property-id", {propertyId: id})
        .success(function(resp) {
          self.municipalityCode(resp.municipality);
        })
        .error(function(e) {
          error("Failed to find municipality", id, e);
        })
        .call();
    }
    return self;
  };

  //
  // Concurrency control:
  //

  self.requestContext = new RequestContext();
  self.beginUpdateRequest = function() {
    self.requestContext.begin();
    return self;
  };

  //
  // Search API
  //

  self.onError = function() {
    hub.send("indicator", {style: "negative", message: "integration.getAddressNotWorking"});
  };

  self.searchPropertyId = function(x, y) {
    locationSearch.propertyIdByPoint(self.requestContext, x, y, self.setPropertyId, self.onError, self.processing);
    return self;
  };

  self.searchAddress = function(x, y) {
    locationSearch.addressByPoint(self.requestContext, x, y, self.setAddress, self.onError, self.processing);
    return self;
  };


  self.searchPoint = function(value) {
    if (!_.isEmpty(value)) {
      self.reset();
      return util.prop.isPropertyId(value) ? self._searchPointByPropertyId(value) : self._searchPointByAddress(value);
    }
    return self;
  };

  //
  // Private functions
  //

  self._searchPointByAddress = function(address) {
    locationSearch.pointByAddress(self.requestContext, address, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0],
              x = data.location.x,
              y = data.location.y;
          self
            .x(x).y(y)
            .setAddress(data)
            .beginUpdateRequest()
            .searchPropertyId(x, y);
          hub.send("location-found");
        }
      }, self.onError, self.processing);
    return self;
  };

  self._searchPointByPropertyId = function(id) {
    locationSearch.pointByPropertyId(self.requestContext, id, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0],
              x = data.x,
              y = data.y;
          self
            .x(x).y(y)
            .setPropertyId(util.prop.toDbFormat(id))
            .beginUpdateRequest()
            .searchAddress(x, y);
          hub.send("location-found");
        }
      }, self.onError, self.processing);
    return self;
  };

};

LUPAPISTE.LocationModel.prototype = _.create(LUPAPISTE.LocationModelBase.prototype, {"constructor":LUPAPISTE.LocationModel});
