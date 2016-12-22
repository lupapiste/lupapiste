// Upload zone and label.
// Params [optional]:
//   upload: UploadModel instance
//  [testId]: test id prefix for input and label (default upload-zone
//  -> upload-zone-input, upload-zone-label).
LUPAPISTE.UploadZoneModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.upload = params.upload;

  // Setting for attribute to "" effectively disables file selection.
  self.labelFor = self.disposedComputed( function() {
    return self.upload.waiting()
      || self.upload.readOnly ? "" : self.upload.fileInputId;
  });

  self.testId = params.testId || "upload-zone";


};
