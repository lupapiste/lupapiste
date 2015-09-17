LUPAPISTE.MaaraalaTunnusModel = function(params) {
  "use strict";
  var self = this;

  self.schema = params.schema;
  self.path = params.path;

  self.isMaaraala = ko.observable(false);

  var propertyId = params.propertyId || lupapisteApp.models.application.propertyId();
  
  self.propertyId = ko.pureComputed(function() {
    var humanizedPropId = util.prop.toHumanFormat(propertyId);
    return self.isMaaraala() ? humanizedPropId + "-M" : humanizedPropId;
  });

  self.propertyIdLabel = ko.pureComputed(function() {
    return self.isMaaraala() ? "Määräala" : "Kiinteistötunnus";
  });
  
  self.maaraalaTunnus = ko.observable("");
  var saveFun = function() {
    
  };
  self.maaraalaTunnus.subscribe(_.debounce(saveFun, 500));
};