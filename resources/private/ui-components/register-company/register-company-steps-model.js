// Container for company registration "wizard" steps.
LUPAPISTE.RegisterCompanyStepsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.currentStep = service.currentStep;

  function account() {
    return service.registration.accountType();
  }

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

  // Back button support

  function pageCheck() {
    return pageutil.getPage() === "register-company-account-type";
  }

  $(window).on( "hashchange", function() {
    if( pageCheck() ) {
      // No coming back from the last step.
      var step = _.toInteger( _.last( /\/([1-3])$/
                                      .exec( window.location.hash )));
      self.currentStep( step && account() ? step - 1 : 0 );
    }
  });


  function updateHash( step ) {
    if( pageCheck() ) {
      window.location.hash =
        pageutil.buildPageHash( pageutil.getPage(),
                                step && account() ? step + 1 : "" );
    }
  }

  self.disposedSubscribe( self.currentStep, updateHash);
};
