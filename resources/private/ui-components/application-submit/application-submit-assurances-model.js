LUPAPISTE.ApplicationSubmitAssurancesModel = function () {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());
  self.lang = loc.getCurrentLanguage();

  self.assurances = [];
  self.selectedAssurances = [];

  self.init = function () {
    var observables = lupapisteApp.services.applicationSubmitAssurancesService.getAssuranceObservables();
    self.assurances = observables.assurances;
    self.selectedAssurances = observables.selectedAssurances;
  };

  //Initialize
  self.init();
};
