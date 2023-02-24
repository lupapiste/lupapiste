LUPAPISTE.MattiAdminModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  // For some reason the parameter is observable.
  var orgId =  ko.unwrap( params.organizationId ) ;

  self.url = ko.observable();
  self.username = ko.observable();
  self.password = ko.observable();
  self.vault = ko.observable();
  self.buildingUrl = ko.observable();
  self.enabled = ko.observable();

  self.properties = ko.observableArray();

  function fetchConfig() {
    ajax.query( "matti-config", {organizationId: orgId})
      .success( function( res ) {
        self.properties.removeAll();
        var cfg = res.config;
        _.each( ["url", "vault", "username", "password", "buildingUrl"],
                function( prop ) {
                  self.properties.push( {
                    name: prop,
                    text: loc( "matti." + prop ),
                    value: ko.observable(cfg[prop])});
                });
        self.enabled( cfg.enabled);
      })
      .call();
  }

  function isEnabled( name ) {
    return util.getIn( self.enabled, [name]);
  }

  self.status = function( name ) {
    return isEnabled( name ) ? "matti.enabled" : "matti.disabled";
  };

  self.toggleFunction = function( fun ) {
    ajax.command( "toggle-matti-functionality", {organizationId: orgId,
                                                 "function": fun,
                                                 enabled: !isEnabled( fun )})
      .success( fetchConfig )
      .call();
  };

  self.buttonText = function( name ) {
    return isEnabled( name ) ? "matti.turn-off" : "matti.turn-on";
  };

  self.buttonCss = function( name ) {
    var flag = isEnabled( name );
    return {positive: !flag,
            negative: flag };
  };

  self.allFilled = self.disposedComputed( function() {
    return _.every( self.properties(),
                    function( prop ) {
                      return !s.isBlank( prop.value() );
                    });
  });

  self.updateConfig = function() {
    ajax.command( "update-matti-config", _.reduce(self.properties(),
                                                  function( acc, prop ) {
                                                    return _.set( acc, prop.name, prop.value());
                                                  },
                                                  {organizationId: orgId}))
      .success( function ( res ) {
        util.showSavedIndicator( res );
        fetchConfig();
      })
      .call();

  };

  // Initialization
  fetchConfig();

  // Batchrun

  self.batchrunMode = ko.observable();
  self.batchrunOptions = _.map( ["dates", "ids"],
                                function( s ) {
                                  return {text: "matti.batchrun." + s,
                                          value: s };
                                });
  self.batchrunStart = ko.observable();
  self.batchrunEnd = ko.observable();
  self.batchrunIds = ko.observable();
  self.batchrunValidate = ko.observable( true );
  self.batchrunSendVerdicts = ko.observable(true);
  self.batchrunSendStateChanges = ko.observable(false);
  self.batchrunWait = ko.observable();
  self.batchrunResults = ko.observable();

  self.batchrunCan = self.disposedComputed( function() {
    var mode = self.batchrunMode();
    if( mode === "dates" ) {
      return !(s.isBlank( self.batchrunStart())
               || s.isBlank( self.batchrunEnd()));
    }
    if( mode === "ids" ) {
      return !s.isBlank( self.batchrunIds() );
    }
    return false;
  });

  self.batchrunRun = function() {
    self.batchrunResults( "" );
    ajax.command( "matti-batchrun",
                  _.merge( {organizationId: orgId,
                            validateXml: self.batchrunValidate(),
                            sendVerdicts: self.batchrunSendVerdicts(),
                            sendStateChanges: self.batchrunSendStateChanges()},
                           self.batchrunMode() === "ids"
                           ? {ids: self.batchrunIds()}
                           : {startDate: self.batchrunStart(),
                              endDate: self.batchrunEnd()}))
      .pending( self.batchrunWait )
      .processing( self.batchrunWait )
      .timeout( 10 * 60 * 1000) // 10 minutes
      .success( function( res ) {
        self.batchrunResults( _( res.results )
                              .map( function( r ) {
                                var s = sprintf( "%-20s [%s]:", r.applicationId, r.timestamp );
                                if( r.state ) {
                                  s += sprintf( " state: %-27s KuntaGML: %s ", r.state , r.kuntagml );
                                } else {
                                  s += " verdict: " + r.verdict;
                                }
                                if( r.error ) {
                                  s += " error: " + r.error;
                                }
                                return s;
                              })
                              .join( "\n") );
      })
      .call();
  };



};
