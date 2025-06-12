// Facilitates the location change with dialog.
// Parameter [optional]:
//  [prefix]:    localization key for prefix (typically infoRequest).
//  edit:        localization key for edit button text
//  application: application instance as defined in application.js
LUPAPISTE.AddressModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.prefix = params.prefix;
  var app = params.application;
  self.address = app.application.address;
  self.edit = params.edit ? loc( params.edit ) : "";

  self.changeAddress = _.wrap( "change-location", hub.send );

  self.canEdit = self.disposedPureComputed( function() {
    var service = lupapisteApp.services.summaryService;
    return service.editMode() && service.authOk( "address" );
  });
};
