LUPAPISTE.MaaraalaTunnusModel = function(params) {
  "use strict";
  var self = this;

  self.model = params.model;
  self.applicationId = params.applicationId || (lupapisteApp.models.application && lupapisteApp.models.application.id()) || null;
  self.documentId = params.documentId;
  self.propertyId = ko.unwrap(params.propertyId);
  self.propertyIdLabel = ko.pureComputed(function() {
    return self.isMaaraala() ? loc("kiinteisto.maaraala.label") : loc("kiinteisto.kiinteisto.label");
  });
  self.isMaaraala = params.isMaaraala;
  if(params.model && !_.isEmpty(params.model.value)) {
    self.isMaaraala(true);
  }

  // hide input label always
  self.schema = _.extend(_.cloneDeep(params.schema), {
    label: false
  });
  self.path = params.path;

  self.visibilityState = ko.pureComputed(function () {
    return self.isMaaraala() ? "visible" : "hidden";
  });
};