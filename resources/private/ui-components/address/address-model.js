// Facilitates the location change with dialog.
// Parameter [optional]:
//  [prefix]:    localization key for prefix (typically infoRequest).
//  edit:        localization key for edit button text
//  application: application instance as defined in application.js
LUPAPISTE.AddressModel = function( params ) {
  "use strict";
  var self = this;
  self.prefix = params.prefix;
  var app = params.application;
  self.address = app.application.address;
  self.edit = params.edit ? loc( params.edit ) : "";
  self.changeAddress = function() {
    app.changeLocationModel.changeLocation( app.application );
  };
  self.isAuthorized = function() {
    return app.authorization.ok( "change-location");
  }
};
