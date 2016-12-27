LUPAPISTE.BaseUploadModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.upload = params.upload;

  self.waiting = params.waiting || self.upload.waiting;

  // Setting for attribute to "" effectively disables file selection.
  self.labelFor = self.disposedComputed( function() {
    return self.waiting() || self.upload.readOnly ? "" : self.upload.fileInputId;
  });

};
