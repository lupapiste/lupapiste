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

};
