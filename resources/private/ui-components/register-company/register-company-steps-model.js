// Container for company registration "wizard" steps.
LUPAPISTE.RegisterCompanyStepsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.currentStep = params.step ? ko.observable( params.step ) : service.currentStep;

  self.stepNames = _.map( _.range(1, 5),
                          _.partial( sprintf, "register.company.phase.%s"));
  self.isLast = function( index ) {
    return index === _.size( self.stepNames ) - 1;
  };

  self.stepCss = function( index ) {
    var step = self.currentStep();
    index = ko.unwrap( index );
    return {current: step === index,
            past: step > index };
  };
};
