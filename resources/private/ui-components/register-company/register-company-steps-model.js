// Container for company registration "wizard" steps.
// Params: [optional]
// [cancelActive]: observable for Cancel button (default always active).
// [cancelClick]: Cancel button click handler. If not give, the button is not shown.
// [nextActive] and [nextClick] as above but for Next button.
// step: Current step. The range is 0-3, inclusive.
LUPAPISTE.RegisterCompanyStepsModel = function( params ) {
  "use strict";
  var self = this;

  self.cancelActive = params.cancelActive || true;
  self.cancelClick = params.cancelClick;

  self.nextActive = params.nextActive || true;
  self.nextClick = params.nextClick;

  self.currentStep = params.step;

  self.stepNames = _.map( _.range(1, 5),
                          _.partial( sprintf, "register.company.phase.%s"));
  self.isLast = function( index ) {
    return index === _.size( self.stepNames ) - 1;
  };

};
