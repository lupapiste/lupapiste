LUPAPISTE.MaaraalaTunnusModel = function(params) {
  "use strict";
  var self = this;

  self.isMaaraala = ko.observable(false);
  var humanizedPropId = util.prop.toHumanFormat(params.propertyId);
  self.propertyId = ko.pureComputed(function() {
    return self.isMaaraala() ? humanizedPropId + "-M" : humanizedPropId;
  });
  self.propertyIdLabel = ko.pureComputed(function() {
    return self.isMaaraala() ? "Määräala" : "Kiinteistötunnus";
  });
  
};