LUPAPISTE.SutiService = function() {
  "use strict";
  var self = this;

  self.serviceName = "sutiService";

  var suti = ko.observable({});

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

  self.configureServer = function( server, processing ) {
    ajax.command( "update-suti-server-details", server )
      .processing( processing )
      .success( function( res ) {
        util.showSavedIndicator( res );
        // Sync the service data and as a side effect clears the
        // password field.
        self.fetchAdminDetails();
      })
      .call();
  };
};
