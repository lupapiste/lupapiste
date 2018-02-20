// Container for company registration "wizard" steps.
// Params [optional]:
// [step]: The current step. Range for real steps is [0,3]
// inclusive. Greater steps make every step green. By default, step is
// provided by the companyRegistrationService
LUPAPISTE.RegisterCompanyStepsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;
  var campService = lupapisteApp.services.campaignService;

  var regularTexts = {title: "register.company.title"};

  self.campaign = lupapisteApp.services.campaignService.campaign;

  self.currentStep = params.step ? ko.observable( params.step ) : service.currentStep;

  self.stepNames = _.map( _.range(1, service.stepConfigs.length + 1 + 1), // + 1 to be inclusive + 1 is for success/fail page, which is not handled 'normally'
                          _.partial( sprintf, "register.company.phase.%s"));

  self.isLast = function( index ) {
    return index === _.size( self.stepNames ) - 1;
  };

  var tabbyClasses = [10, 20, 25, 33, 40, 50, 75, 80, 90, 100]; // defined in _tables.scss

  self.dynamicLineCss = self.disposedPureComputed(function() { // returns correct class depending on amount of 'steps' to be shown
    var divider = (100 / (self.stepNames.length || 1));
    var selectedClass = _.reduce(tabbyClasses, function(acc, item) {
      return acc < divider ? item : acc;
    });
    return _.set({"tabby__cell": true}, "tabby--" + selectedClass, true);
  });

  self.stepCss = function( index ) {
    var step = self.currentStep();
    index = ko.unwrap( index );
    return {current: step === index,
            past: step > index };
  };

  self.texts = self.disposedPureComputed( function() {
    var txt = campService.campaignTexts() || regularTexts;
    return self.currentStep() ? _.omit( txt, "subtitle") : txt;
  });
};
