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

  self.fetchApplicationData = function( applicationId ) {
    // Clear old data just in case
    suti( {} );
    ajax.query( "suti-application-data", {id: applicationId })
      .success( function( res ) {
        if( _.isArray( res.data.products )) {
          res.data.products = _.map( res.data.products, function( p ) {
            // If exists, expiry date is in local (Finnish) time.
            return p.expirydate
              ? _.set( p, "expirydate", moment.unix( p.expirydate))
              : p;
          });
        }
        suti( _.assign( res.data ) );
      })
      .call();
  };

  self.products = ko.pureComputed( function() {
    return suti().products;
  });

};
