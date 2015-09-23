LUPAPISTE.MaaraalaTunnusModel = function(params) {
  "use strict";
  var self = this;

  self.model = params.model;
  self.documentId = params.documentId;

  self.schema = _.extend(_.cloneDeep(params.schema), {
    label: false
  });
  self.path = params.path;

  self.isMaaraala = params.isMaaraala;
  if(params.model && !_.isEmpty(params.model.value)) {
    self.isMaaraala(true);
  }

  var propertyId = params.propertyId();
  self.applicationId = params.applicationId || lupapisteApp.models.application.id();
  
  self.propertyId = ko.pureComputed(function() {
    var humanizedPropId = util.prop.toHumanFormat(propertyId);
    return self.isMaaraala() ? humanizedPropId + "-M" : humanizedPropId;
  });

  self.propertyIdLabel = ko.pureComputed(function() {
    return self.isMaaraala() ? loc("kiinteisto.maaraala.label") : loc("kiinteisto.kiinteisto.label");
  });
};