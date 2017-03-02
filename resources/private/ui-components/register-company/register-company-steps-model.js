// Container for company registration "wizard" steps.
LUPAPISTE.RegisterCompanyStepsModel = function( params ) {
  "use strict";
  var self = this;

  self.currentStep = lupapisteApp.services.companyRegistrationService.currentStep;

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
