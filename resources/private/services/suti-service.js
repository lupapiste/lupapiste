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

  self.fetchApplicationData = function( application ) {
    // Clear old data just in case
    suti( {} );
    ajax.query( "suti-application-data", {id: application.id()})
      .success( function( res ) {
        if( _.isArray( res.data.products )) {
          res.data.products = _.map( res.data.products, function( p ) {
            // If exist, expirydate and downloaded are in local (Finnish) time.
            return _.assignWith( p,
                                 {expirydate: true, downloaded: true},
                                 function( v ) {
                                   return v ? moment.unix( v ) : v;
                                 });

          });
        }
        // Fully formed application Suti data properties:
        // enabled: true, if this application requires suti
        // www: public url in the Suti system
        // products: array of Suti products
        // title: Title to be shown on Suti rollup button (see suti-display)
        // suti: application Suti details (id and added).
        suti( _.merge( res.data,
                       {title: loc( "suti.display-title",
                                    util.prop.toHumanFormat( application.propertyId()))},
                       _.clone( ko.unwrap( _.get )( application, "suti", {}))));
      })
      .call();
  };

  self.updateApplication = function( application, data, refresh ) {
    ajax.command( "suti-update-application", {id: application.id(),
                                              suti: data })
      .success( function() {
        if( refresh ) {
          self.fetchApplicationData( application );
        }
      })
      .call();
  };
};
