LUPAPISTE.LocationModel = function() {
  "use strict";
  var self = this;
  var floatProperties = ["x", "y"];
  var falseyProperties = ["addressString", "propertyId", "municipalityCode"];

  self.activeReques = null;
  self.processing = ko.observable(false);

  self.x = ko.observable(0);
  self.y = ko.observable(0);
  self.addressString = ko.observable(null);

  self.propertyId = ko.observable(null);
  self.propertyIdHumanReadable = ko.pureComputed({
    read: function(){
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
    },
    write: function(value) {
      self.propertyId(util.prop.toDbFormat(value));
    },
    owner: self});

  self.municipalityCode = ko.observable(null);

  self.municipalityName = ko.pureComputed(function() {
    if (self.municipalityCode()) {
      return loc(["municipality", self.municipalityCode()]);
    }
    return "";
  });

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


  });

};
