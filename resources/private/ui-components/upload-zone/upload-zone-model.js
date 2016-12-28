// Upload zone and label.
// Params [optional]:
//   upload: UploadModel instance
//  [testId]: test id prefix for input and label (default upload-zone
//  -> upload-zone-input, upload-zone-label).
LUPAPISTE.UploadZoneModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.BaseUploadModel(params));

  self.testId = params.testId || "upload-zone";


};
