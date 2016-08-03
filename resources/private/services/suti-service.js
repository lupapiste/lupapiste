LUPAPISTE.SutiService = function() {
  "use strict";
  var self = this;

  self.serviceName = "sutiService";

  var suti = ko.observable({});
  var operations = ko.observableArray([]);

  self.sutiDetails = ko.pureComputed( function() {
    return _.cloneDeep( suti() );
  });

  self.fetchAdminDetails = function() {
    ajax.query( "suti-admin-details")
      .success( function( res ) {
        suti( res.suti || {} );
      })
      .call();
  };

  self.fetchOperations = function() {
    ajax.query( "suti-operations")
        .success( function( res ) {
          operations( res.operations || []);
        })
      .call();
  };

  self.configureServer = function( server, processing ) {
    ajax.command( "update-suti-server-details", server )
      .processing( processing )
      .success( function( res ) {
        util.showSavedIndicator( res );
        // Syncs the service details and as a side effect clears the
        // password field.
        self.fetchAdminDetails();
      })
      .call();
  };

  self.sutiEnabled = ko.computed( {
    read: function() {
      return suti().enabled;
    },
    write: function( flag ) {
      if( _.isBoolean( flag )) {
        ajax.command( "suti-toggle-enabled", {flag: flag})
          .success( function( res ) {
            util.showSavedIndicator( res );
            suti( _.assign( suti(), {enabled: flag}));
          })
          .call();
      }
    }
  });

  self.sutiWww = ko.computed( {
    read: function() {
      return suti().www;
    },
    write: function( www ) {
      www = _.trim( www );
      ajax.command( "suti-www", {www: www})
        .success( function( res ) {
          util.showSavedIndicator( res );
          suti( _.assign( suti(), {www: www}));
        })
        .call();
    }
  });

  self.isSutiOperation = function ( dataOrId )  {
    return operations.indexOf( _.get( dataOrId, "id", dataOrId) ) >= 0;
  };

  self.toggleSutiOperation = function( dataOrId ) {
    var id = _.get( dataOrId, "id", dataOrId );
    var flag = !self.isSutiOperation( id );
    if( flag ) {
      operations.push( id );
    } else {
      operations.remove( id );
    }
    ajax.command( "suti-toggle-operation", {operationId: id,
                                            flag: flag })
      .call();
  };

  function fetchApplicationProducts( application, waiting ) {
    ajax.query( "suti-application-products", {id: application.id()})
      .pending( waiting || _.noop)
      .success( function( res ) {
        var result = [];
        var data = res.data || {};
        if( data.id && data.id === _.get( suti(), "suti.id" )) {
          if( _.isArray( data.products )) {
            data.products = _.map( data.products, function( p ) {
              // If exist, expirydate and downloaded are in UTC timestamps (ms)
              return _.assignWith( p,
                                   {expirydate: true, downloaded: true},
                                   function( v ) {
                                     return v ? moment( v ) : v;
                                   });

            });
          }
          // products: array of Suti products OR error ltext.
          result = _.get( data, "products", []);
        }
        suti( _.merge( suti(), {products: result}));
      })
      .call();
  }

  // waiting is an optional observable.
  self.fetchApplicationData = function( application, waiting ) {
    // Application Suti object is outdated from now on
    delete application.suti;
    ajax.query( "suti-application-data", {id: application.id()})
      .pending( waiting || _.noop)
      .success( function( res ) {
        // Fully formed application Suti data properties:
        // enabled: true, if this application requires suti
        // www: public url in the Suti system
        // products: array of Suti products OR error ltext.
        // title: Title to be shown on Suti rollup button (see suti-display)
        // suti: application Suti details (id and added).
        suti( _.merge( res.data,
                       {products: "suti.products-wait"}));
        // We fetch the products separately so the UI has time to render.
        fetchApplicationProducts( application, waiting );
      })
      .call();
  };

  var keyParameters = { id: "sutiId", added: "added"};

  // waiting is an optional observable.
  self.updateApplication = function( application, key, value, waiting ) {
    var param = keyParameters[key];
    if( param ) {
      ajax.command( "suti-update-" + key,
                    _.set( {id: application.id()}, param, value ))
        .pending( waiting || _.noop )
        .success( function() {
          self.fetchApplicationData( application, waiting );
        })
        .call();
    }
  };
};
