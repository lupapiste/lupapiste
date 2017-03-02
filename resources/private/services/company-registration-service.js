LUPAPISTE.CompanyRegistrationService = function() {
  "use strict";
  var self = this;

  var accountPrices = ko.observable({account5: 59, account15: 79, account30: 99 });

  function newRegistration() {
    return {
      accountType: ko.observable(),
      companyName: ko.observable(),
      companyY: ko.observable(),
      address: ko.observable(),
      zip: ko.observable(),
      po: ko.observable(),
      country: ko.observable(),
      eInvoice: ko.observable(),
      ovt: ko.observable(),
      pop: ko.observable(),
      reference: ko.observable(),
      firstName: ko.observable(),
      lastName: ko.observable(),
      email: ko.observable(),
      personId: ko.observable(),
      language: ko.observable( loc.currentLanguage )
    };
  }

  // Guard makes sure that the wizard components are disposed.
  self.guard = ko.observable( true );
  self.registration = newRegistration();

  var warnings = {
    companyY: ko.observable(),
    zip: ko.observable(),
    ovt: ko.observable(),
    email: ko.observable(),
    personId: ko.observable()
  };

  function isValidZip( s ) {
    s = _.trim( s );
    return _.size( s ) > 4 && /^[0-9]+$/.test( s );
  }

  // TODO: Email already in use.
  // TODO: Current user.

  var validators = {
    companyY: {fun: util.isValidY,
              msg: "error.invalidY"},
    zip: {fun: isValidZip,
          msg: "error.illegal-zip"},
    ovt: {fun: util.isValidOVT,
          msg: "error.invalidOVT"},
    email: {fun: util.isValidEmailAddress,
            msg: "error.illegal-email"},
    personId: {fun: util.isValidPersonId,
              msg: "error.illegal-hetu"}
  };

  var requiredFields = ["companyName", "companyY",
                        "address", "zip", "po",
                       "firstName", "lastName", "email", "personId"];

  // Warnings are updated.
  ko.computed( function() {
    _.each( validators, function( validator, k ) {
      var txt = _.trim( self.registration[k]());
      warnings[k]( txt && self.guard() && !validator.fun( txt )
                 ? validator.msg
                 : "");
    });
  });

  self.field = function( fieldName ) {
    return {required: _.includes( requiredFields, fieldName ),
            value: self.registration[fieldName],
            label: "register.company.form." + fieldName,
            warning: warnings[fieldName]};
  };


  self.currentStep = ko.observable( 0 );


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

  function nextStep() {
    self.currentStep( self.currentStep() + 1 );
  }

  function fieldsOk() {
    return _.every( requiredFields, function( field ) {
      return _.trim(self.registration[field]());
    } )
        && _.every( _.values( warnings) , _.flow( ko.unwrap, _.isEmpty ));
  }

  var stepConfigs = [{component: "register-company-account-type",
                      continueEnable: self.registration.accountType,
                      continueClick: nextStep},
                     {component: "register-company-info",
                      continueEnable: fieldsOk,
                      continueClick: _.noop}];

  self.currentConfig = function() {
    return stepConfigs[self.currentStep()];
  };
};
