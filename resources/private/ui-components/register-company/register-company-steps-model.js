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

  var regularTexts = {title: "register.company.title"}; // supports also 'subttitle'

  self.currentStep = params.step ? ko.observable( params.step ) : service.currentStep;

  // + 1 to be inclusive and another + 1 is for success/fail page, which is not handled 'normally' :(
  self.stepNames = _.map( _.range(1, service.stepConfigs.length + 1 + 1),
                          _.partial( sprintf, "register.company.phase.%s"));

  self.isLast = function( index ) {
    return index === _.size( self.stepNames ) - 1;
  };

  var tabbyClasses = [10, 20, 25, 33, 40, 50, 75, 80, 90, 100]; // defined in _tables.scss

  // returns correct class depending on amount of 'steps' to be shown
  self.dynamicLineCss = self.disposedPureComputed(function() {
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

  self.texts = regularTexts;
};
