/**
 * Base model for selecting application location
 */
LUPAPISTE.LocationModelBase = function() {
  "use strict";
  var self = this;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);

  self.address = ko.observable("");

  self.propertyId = ko.observable("");
  self.propertyIdHumanReadable = ko.pureComputed(function() {
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
    });

};
