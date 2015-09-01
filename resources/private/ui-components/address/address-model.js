// Facilitates the location change with dialog.
// Parameter:
//  prefix: localization key for prefix (typically infoRequest).
LUPAPISTE.AddressModel = function( params ) {
  "use strict";

  this.prefix = params.prefix;
  this.address = params.address;
  this.edit = params.edit ? loc( params.edit ) : "";
  this.location = new LUPAPISTE.ChangeLocationModel();
};
