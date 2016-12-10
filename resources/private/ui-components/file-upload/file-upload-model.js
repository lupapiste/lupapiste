// Convenient file upload mechanism that utilises the FileuploadService.
// Parameters can include both UploadModel and UploadButtonModel params.
LUPAPISTE.FileUploadModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.upload = new LUPAPISTE.UploadModel( self, params );

  self.buttonOptions = _.merge( params, {upload: self.upload});

  self.upload.init();
};
