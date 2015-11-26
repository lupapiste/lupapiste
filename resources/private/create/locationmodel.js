LUPAPISTE.LocationModel = function() {
  "use strict";

  var self = this;
  LUPAPISTE.LocationModelBase.call(self,
      {mapId:"create-map",
       initialZoom: 2,
       zoomWheelEnabled: true,
       clickHandler: function(x, y) {
         hub.send("track-click", {category:"Create", label:"map", event:"mapClick"});
         self.reset().setXY(x, y).beginUpdateRequest()
           .searchPropertyId(x, y)
           .searchAddress(x, y);
         return false;
       },
       popupContentModel: "section#map-popup-content"});

  self.municipalityCode = ko.observable("");

  self.municipalityName = ko.pureComputed(function() {
    return self.municipalityCode() ? loc(["municipality", self.municipalityCode()]): "";
  });
  self.municipalitySupported = ko.observable(true);

  self.reset = function() {
    return self.setXY(0,0).address("").propertyId("").municipalityCode("");
  };

  self.setAddress = function(a) {
    return self.municipalityCode(a ? a.municipality : "").address(a ? a.street + " " + a.number : "");
  };

  self.addressOk = ko.pureComputed(function() { return self.municipalityCode() && !_.isBlank(self.address()); });

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
            .setXY(x,y).center(13)
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
            .setXY(x,y).center(14)
            .setPropertyId(util.prop.toDbFormat(id))
            .beginUpdateRequest()
            .searchAddress(x, y);
          hub.send("location-found");
        }
      }, self.onError, self.processing);
    return self;
  };

  self.proceed = _.partial(hub.send, "create-step-2");

};

LUPAPISTE.LocationModel.prototype = _.create(LUPAPISTE.LocationModelBase.prototype, {"constructor":LUPAPISTE.LocationModel});
