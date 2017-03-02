LUPAPISTE.CompanyRegistrationService = function() {
  "use strict";
  var self = this;

  var accountPrices = ko.observable({account5: 59, account15: 79, account30: 99 });

  function newRegistration() {
    return {
      accountType: ko.observable()
    };
  }

  self.currentStep = ko.observable( 0 );

  // Guard makes sure that the wizard components are disposed.
  self.guard = ko.observable( true ),
  self.registration = newRegistration();

  self.accountTypes = ko.computed( function() {
    return _.map( [5, 15, 30], function( n ) {
      return { id: "account" + n,
               title: loc( sprintf( "register.company.account%s.title", n )),
               price: loc( sprintf( "register.company.account%s.price", n),
                          _.get( accountPrices(), "account" + n )),
               description: loc( "register.company.account.description", n )};
    });
  });

  function reset() {
    self.guard( false );
    self.registration = newRegistration() ;
    self.currentStep( 0 );
    self.guard( true );
  }

  self.cancel = function() {
    if( self.currentStep()) {
      self.currentStep( self.currentStep() - 1 );
    } else {
      reset();
      pageutil.openPage( "register");
    }
  };

  var stepConfigs = [{component: "register-company-account-type",
                      continueEnable: self.registration.accountType,
                      continueClick: _.noop},
                     {}];

  self.currentConfig = function() {
    return stepConfigs[self.currentStep()];
  };
};
