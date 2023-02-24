LUPAPISTE.MaaraalaTunnusModel = function(params) {
  "use strict";
  var self = this;

  self.isDisabled = params.isDisabled;
  self.authModel = params.authModel;
  self.applicationId = params.applicationId || (lupapisteApp.models.application && lupapisteApp.models.application.id()) || null;
  self.documentId = params.documentId;
  self.propertyId = ko.unwrap(params.propertyId);
  self.propertyIdEnteredManually = ko.pureComputed(function() {
    return ko.unwrap(params.propertyIdSource) === "user";
  });
  self.propertyIdLabel = ko.pureComputed(function() {
    return self.isMaaraala() ? loc("kiinteisto.maaraala.label") : loc("kiinteisto.kiinteisto.label");
  });

  // hide input label always
  self.schema = _.extend(_.cloneDeep(params.schema), {
    label: false
  });
  self.path = params.path;

  var service = lupapisteApp.services.documentDataService;
  self.maaraalaTunnus = service.getInDocument(self.documentId, self.path).model;

  self.isMaaraala = params.isMaaraala;
  if (!_.isEmpty(self.maaraalaTunnus())) {
    self.isMaaraala(true);
  }

  self.isMaaraala.subscribe(function(isMaaraala) {
    if (!isMaaraala) {
      self.maaraalaTunnus("");
    }
  });

  self.visibilityState = ko.pureComputed(function () {
    return self.isMaaraala() ? "visible" : "hidden";
  });
};
