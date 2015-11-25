LUPAPISTE.LocationModel = function() {
  "use strict";
  var self = this;
  var floatProperties = ["x", "y"];
  var falseyProperties = ["addressString", "propertyId", "municipalityCode"];

  self.activeRequest = null;
  self.processing = ko.observable(false);

  self.x = ko.observable(0);
  self.y = ko.observable(0);
  self.addressString = ko.observable("");

  self.propertyId = ko.observable("");
  self.propertyIdHumanReadable = ko.pureComputed({
    read: function(){
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
    },
    write: function(value) {
      self.propertyId(util.prop.toDbFormat(value));
    },
    owner: self});

  self.municipalityCode = ko.observable("");

  self.municipalityName = ko.pureComputed(function() {
    return self.municipalityCode() ? loc(["municipality", self.municipalityCode()]): "";
  });

  self.reset = function() {
    self.x(0).y(0).addressString("").propertyId("").municipalityCode("");
  };

  // TODO remove when done
  ko.computed(function() {
    var missingDetails = [];

    _.each(floatProperties, function(p) {
      if (self[p]() < 0.1) {
        missingDetails.push(p);
      }
    });
    _.each(falseyProperties, function(p) {
      if (!self[p]()) {
        missingDetails.push(p);
      }
    });

    console.log("missingDetails", missingDetails);

  }).extend({ throttle: 100 });

  self.addressData = function(a) {
    return self.addressString(a ? a.street + " " + a.number : "");
  };

  //
  // Concurrency control:
  //

  self.requestContext = new RequestContext();
  self.beginUpdateRequest = function() {
    self.requestContext.begin();
    return self;
  };

  self.searchPropertyId = function(x, y) {
    locationSearch.propertyIdByPoint(self.requestContext, x, y, self.propertyId);
    return self;
  };

  self.searchAddress = function(x, y) {
    locationSearch.addressByPoint(self.requestContext, x, y, self.addressData);
    return self;
  };


  self.searchPointByAddressOrPropertyId = function(value) {
    if (!_.isEmpty(value)) {
      self.reset();
      return util.prop.isPropertyId(value) ? self.searchPointByPropertyId(value) : self.searchPointByAddress(value);
    } else {
      return self;
    }
  };

  self.searchPointByAddress = function(address) {
    locationSearch.pointByAddress(self.requestContext, address, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0],
              x = data.location.x,
              y = data.location.y;
          self
            .x(x).y(y)
            .addressData(data)
            .beginUpdateRequest()
            .searchPropertyId(x, y);
          hub.send("location-found");
        }
      });
    return self;
  };

  self.searchPointByPropertyId = function(id) {
    locationSearch.pointByPropertyId(self.requestContext, id, function(result) {
        if (result.data && result.data.length > 0) {
          var data = result.data[0],
              x = data.x,
              y = data.y;
          self
            .x(x).y(y)
            .propertyId(util.prop.toDbFormat(id))
            .beginUpdateRequest()
            .searchAddress(x, y);
          hub.send("location-found");
        }
      });
    return self;
  };

};
