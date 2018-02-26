// Service that facilitates the company registration wizard.
LUPAPISTE.CompanyRegistrationService = function() {
  "use strict";
  var self = this;

  var serviceName = "companyRegistrationService";

  // User summary or null.
  function user() {
    var u = lupapisteApp.models.currentUser;
    return u.id()
         ? ko.mapping.toJS( _.pick( u, ["firstName", "lastName",
                                        "email", "language"]))
         : null;
  }

  // Property names are the same as used by the backend.
  function newRegistration() {
    return {
      billingType: ko.observable().extend({
        limited: {values: ["monthly", "yearly"], defaultValue: "yearly"}
      }),
      accountType: ko.observable(),
      name: ko.observable(),
      y: ko.observable(),
      address1: ko.observable(),
      zip: ko.observable(),
      po: ko.observable(),
      country: ko.observable(),
      netbill: ko.observable(),
      pop: ko.observable(),
      reference: ko.observable(),
      firstName: ko.observable(),
      lastName: ko.observable(),
      email: ko.observable(),
      personId: ko.observable(),
      language: ko.observable( loc.currentLanguage ),
      contactAddress: ko.observable(),
      contactZip: ko.observable(),
      contactPo: ko.observable(),
      contactCountry: ko.observable()
    };
  }

  function requiredFields() {
    var fields = ["name", "y",
                  "address1", "zip", "po"];
    return user()
         ? fields
         : _.concat( fields, [ "firstName", "lastName",
                               "email", "personId"]);
  }

  // Guard makes sure that the wizard components are disposed.
  self.guard = ko.observable( true );
  self.registration = {
      billingType: ko.observable().extend({
        limited: {values: ["monthly", "yearly"], defaultValue: "yearly"}
      }),
      accountType: ko.observable(),
      name: ko.observable(),
      y: ko.observable(),
      address1: ko.observable(),
      zip: ko.observable(),
      po: ko.observable(),
      country: ko.observable(),
      netbill: ko.observable(),
      pop: ko.observable(),
      reference: ko.observable(),
      firstName: ko.observable(),
      lastName: ko.observable(),
      email: ko.observable(),
      personId: ko.observable(),
      language: ko.observable( loc.currentLanguage ),
      contactAddress: ko.observable(),
      contactZip: ko.observable(),
      contactPo: ko.observable(),
      contactCountry: ko.observable()
    };
  // Current step [0-3] in the registration wizard.
  self.currentStep = ko.observable( 0 );

  // User login updates the current registration just in case.
  function updateRegistrationUserInfo() {
    var u = user();
    if( u ) {
      self.registration.firstName( u.firstName );
      self.registration.lastName( u.lastName );
      self.registration.email( u.email );
      self.registration.personId( ""); // Just in case
      self.registration.language( u.language );
    }
  }
  ko.computed( updateRegistrationUserInfo );

  // Validation warnings for corresponding registration properties.
  var warnings = {
    y: ko.observable(),
    zip: ko.observable(),
    email: ko.observable(),
    personId: ko.observable(),
    contactZip: ko.observable()
  };

  function isValidZip( s ) {
    s = _.trim( s );
    return _.size( s ) > 4 && /^[0-9]+$/.test( s );
  }

  function isValidEmail( s ) {
    var isOk = util.isValidEmailAddress( s );
    if( isOk && !user() ) {
      ajax.query( "email-in-use", {email: s })
      .success( _.partial( warnings.email, "email-in-use" ))
      .error( _.noop )
      .call();
    }
    return isOk || user();
  }

  // Property names match to the registration. When fun returns
  // falsey, the corresponding warning is updated with msg.
  var validators = {
    y: {fun: util.isValidY,
        msg: "error.invalidY"},
    zip: {fun: isValidZip,
          msg: "error.illegal-zip"},
    contactZip: {fun: isValidZip,
                  msg: "error.illegal-zip"},
    email: {fun: isValidEmail,
            msg: "error.illegal-email"},
    personId: {fun: util.isValidPersonId,
               msg: "error.illegal-hetu"}
  };


  // Warnings are updated when "tracked" properties change.
  ko.computed( function() {
    _.each( validators, function( validator, k ) {
      var txt = _.trim( self.registration[k]());
      warnings[k]( txt && self.guard() && !validator.fun( txt )
                 ? validator.msg
                 : "");
    });
  });

  // Convenience function for populating form-cell component
  // parameters. See register-company-info component for usage.
  self.field = function( fieldName ) {
    return {required: _.includes( requiredFields(), fieldName ),
            value: self.registration[fieldName],
            label: "register.company.form." + fieldName,
            warning: warnings[fieldName],
            testId: "register-company-" + fieldName};
  };

  self.accountTypes = ko.pureComputed(function() {
    var billingType = self.registration.billingType();
    return _.map( LUPAPISTE.config.accountTypes, function( account ) {
      return { id: account.name,
               title: loc( sprintf( "register.company.%s.title",
                                    account.name )),
               price: _.get(account.price, billingType),
               priceRaw: account.price,
               isYearly: billingType === "yearly",
               normalYearPrice: (_.get(account.price, "monthly") * 12),
               description: loc( "register.company.account.description",
                                 account.limit )};
    });
  });

  self.selectedAccount = ko.pureComputed(function() {
    return _.find(self.accountTypes(), {id: self.registration.accountType()});
  });

  // Guard makes sure that components are disposed, when they are
  // located within suitable if.
  function reset() {
    self.guard( false );
    updateRegistrationUserInfo();
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
    return _.every( requiredFields(), function( field ) {
      return _.trim(self.registration[field]());
    } )
        && _.every( _.values( warnings) , _.flow( ko.unwrap, _.isEmpty ));
  }

  // We cache the sent init-sign parameters to avoid superfluous ajax calls.
  // (cancel -> continue -> cancel -> continue ...)
  var latestSignParams = {};

  self.signResults = ko.observable( {} );
  self.pending = ko.observable();

  function initSign() {
    var reg = _.omitBy( ko.mapping.toJS( self.registration ), _.isBlank );
    var campaign = lupapisteApp.services.campaignService.campaign();
    if( campaign.code ) {
      _.set( reg, "campaign", campaign.code );
    }
    _.map( requiredFields(), function( field ) {
      _.set( reg, field, _.trim(self.registration[field]()));
    });
    var params = {lang: reg.language,
                  company: _.pick( reg,
                                   ["accountType", "name", "y", "address1",
                                    "zip", "po", "country", "netbill",
                                    "pop", "reference", "campaign", "contactAddress",
                                    "contactZip", "contactPo", "contactCountry",
                                    "billingType"]),
                  signer: _.pick( reg,
                                 ["firstName", "lastName", "email",
                                  "personId", "language"])};
    if( !_.isEqual( latestSignParams, params )) {
      ajax.command( "init-sign", params )
      .pending( self.pending )
      .success( function( res ) {
        latestSignParams = params;
        self.signResults( _.reduce(  _.omit( res, "ok" ),
                                     function( acc, v, k ) {
                                      return _.set( acc, _.camelCase( k ), v );
                                     },
                                     {}));
        nextStep();
      })
      .call();
    } else {
      nextStep();
    }
  }

  // Definitions for the first three wizard steps. The last step
  // (Activation) is accessed via an email link and does not use the
  // service. ---> Why not??? :)
  self.stepConfigs = [{component: "register-company-account-type",
                      continueEnable: self.registration.accountType,
                      continueClick: nextStep},
                     {component: "register-company-info",
                      continueEnable: fieldsOk,
                      continueClick: nextStep},
                     {component: "register-company-summary",
                      continueEnable: _.constant(true),
                      continueClick: initSign},
                     {component: "register-company-sign",
                      noButtons: true}];

  self.currentConfig = ko.pureComputed(function() {
    return self.stepConfigs[self.currentStep()];
  });

  // Save the current step and registration data to session
  // storage. This is needed, when the user signs in and the page is
  // reloaded.
  function saveItem( name, value ) {
    sessionStorage.setItem( serviceName + "::" + name, value );
  }
  self.save = function() {
    saveItem( "saved", true );
    saveItem( "currentStep", self.currentStep());
    saveItem( "registration", ko.mapping.toJSON( self.registration ) );
  };

  // Read and clear the stored data when service is (re)initialized.
  function readItem( name ) {
    var key = serviceName + "::" + name;
    var value = sessionStorage.getItem( key );
    sessionStorage.removeItem( key );
    return value;
  }

  if( readItem( "saved" )) {
    self.currentStep( _.toInteger(readItem( "currentStep")));
    _.each(JSON.parse(readItem( "registration")),
          function( v, k ) {
            _.get( self.registration, k, _.noop)( v );
          });
    updateRegistrationUserInfo();
  }

  if( LUPAPISTE.config.mode === "dev") {
    self.devFill = function() {
      self.registration.name( "Foobar Oy");
      self.registration.y( "0000000-0");
      self.registration.address1( "Katuosoite 1");
      self.registration.zip( "12345");
      self.registration.po( "Kaupunki");
      if( !user()) {
        self.registration.firstName( "Etunimi");
        self.registration.lastName( "Sukunimi");
        self.registration.email( sprintf( "foo%s@example.com",
                                          _( _.range( 4 ) )
                                          .map( _.partial( _.random, 0, 9,
                                                           false ))
                                          .join("")
                                        ));
        self.registration.personId( "110785-950A" );
      }
    };
  }

};
