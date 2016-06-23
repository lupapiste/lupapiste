LUPAPISTE.SutiService = function() {
  "use strict";
  var self = this;

  self.serviceName = "sutiService";

  var suti = ko.observable({});

  self.sutiDetails = ko.pureComputed( function() {
    return _.cloneDeep( suti() );
  });

  self.fetchAdminDetails = function() {
    if( lupapisteApp.models.globalAuthModel.ok( "suti-admin-details")) {
      ajax.query( "suti-admin-details")
        .success( function( res ) {
          suti( res.suti || {} );
        })
        .call();
    }
  };
};
