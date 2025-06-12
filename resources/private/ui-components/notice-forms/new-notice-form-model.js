// New notice form editor. Fetches initial notice data and submits the
// final form results. Params:
// type: Form type (construction, terrain or location)
// ok: Callback function for successful submit.
// cancel: Callback function for either cancel or unrecoverable error.
LUPAPISTE.NewNoticeFormModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var okFn = params.ok;
  self.cancelForm = params.cancel;
  self.formType = params.type;
  self.infoText = ko.observable();
  self.buildings = ko.observableArray();
  self.foremen = ko.observableArray();
  self.message = ko.observable();
  self.files = ko.observableArray();
  self.waiting = ko.observable();

  self.showCustomer = ko.observable( false );
  self.customer = {};

  self.foremanText = function( foreman ) {
    return sprintf( "%s, %s (%s)",
                    foreman.fullname || loc( "pate.no-name"),
                    loc(foreman.foremanLoc),
                    loc(foreman.stateLoc));
  };


  self.buildingText = function( building ) {
    return _([loc( "operations." + building.opName ),
              _.trim( building.description ),
              _.trim( building.nationalId )])
      .filter( _.identity )
      .join( " - ");
  };

  function selectedBuildingIds() {
    return _(ko.mapping.toJS( self.buildings ))
      .filter( {selected: true})
      .map( function( m ) {
        return m.buildingId;
      })
      .value();
  }

  function initialize() {
    ajax.query( "new-notice-data", {id: pageutil.hashApplicationId(),
                                    type: self.formType,
                                    lang: loc.getCurrentLanguage()})
      .success( function( res ) {
        self.infoText( res["info-text"] );
        self.buildings( _.map( res.buildings,
                               function( m ) {
                                 return _.set( m, "selected", ko.observable());
                               }));
        self.foremen( _.filter(res.foremen, function(foreman) {
          return foreman.status !== "rejected";
        }));
        if( res.customer ) {
          self.customer = ko.mapping.fromJS( _.defaultsDeep( res.customer,
                                                             {name: "", email: "", phone: "",
                                                              payer: {permitPayer: true,
                                                                      name: "",
                                                                      street: "",
                                                                      zip: "",
                                                                      city: "",
                                                                      identifier: ""}}));
          self.showCustomer( true );
        }
      })
      .error( function( res ) {
        util.showSavedIndicator( res );
        self.cancelForm();
      })
      .call();
  }


  self.zipWarning = self.disposedComputed( function() {
    var zipCode = _.trim(util.getIn( self.customer, ["payer", "zip"]));
    if( self.showCustomer() && !self.customer.payer.permitPayer()
        && zipCode && !util.isValidZipCode( zipCode )) {
      return "notice-form.warning.zip";
    }
  });

  self.phoneWarning = self.disposedComputed( function() {
    var phone = _.trim( util.getIn( self.customer, ["phone"]));
    if( self.showCustomer() && phone && !util.isValidPhoneNumber( phone )) {
      return "notice-form.warning.phone";
    }
  });

  self.emailWarning = self.disposedComputed( function() {
    var email =  _.trim( util.getIn( self.customer, ["email"]));
    if( self.showCustomer() && email &&  !util.isValidEmailAddress( email)) {
      return "notice-form.warning.email";
    }
  });

  self.identifierWarning = self.disposedComputed( function() {
    var id =  _.trim(util.getIn( self.customer, ["payer", "identifier"]));
    if( self.showCustomer() && !self.customer.payer.permitPayer()
        && id && !(util.isValidPersonId( id ) || util.isValidY( id ))) {
      return "notice-form.warning.identifier";
    }
  });

  function trimThrow( path ) {
    var v = util.getIn( self.customer, path );
    if( s.isBlank( v )) {
      throw "bad";
    }
    return _.trim( v );
  }

  function packCustomer() {
    try {
      if( self.showCustomer() ) {
        return {name: trimThrow( ["name"]),
                email: trimThrow( ["email"]),
                phone: trimThrow( ["phone"]),
                payer: self.customer.payer.permitPayer()
                ? {permitPayer: true}
                : {name: trimThrow( ["payer", "name"]),
                   street: trimThrow( ["payer", "street"]),
                   zip: trimThrow( ["payer", "zip"]),
                   city: trimThrow( ["payer", "city"]),
                   identifier: trimThrow( ["payer", "identifier"]),}};
      }
    }
    catch( error ) {}
  }

  function customerOk() {
    return !self.showCustomer()
      || (!self.zipWarning() && !self.phoneWarning() && !self.emailWarning() && !self.identifierWarning()
          && packCustomer());
  }

  self.formFilled = self.disposedComputed( function() {
    return _.trim( self.message() ) && customerOk();
  });

  self.submitForm = function() {
    var params = {id: pageutil.hashApplicationId(),
                  buildingIds: selectedBuildingIds(),
                  text: _.trim( self.message()),
                  filedatas: self.files()};
    ajax.command( sprintf( "new-%s-notice-form", self.formType),
                  self.showCustomer() ? _.set( params, "customer", packCustomer()) : params)
      .pending( self.waiting )
      .success( function( res ) {
        util.showSavedIndicator( res );
        okFn();
      })
      .error( util.showSavedIndicator )
      .call();
  };

  initialize();
};
