// Convenient file upload mechanism that utilises the FileuploadService.
// Parameters can include both UploadModel and UploadButtonModel params.
// Parameter notes:
//   files: Observable array that is passed to UploadModel.
LUPAPISTE.FileUploadModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.upload = new LUPAPISTE.UploadModel( self, params );

  self.buttonOptions = _.merge( params, {upload: self.upload});
  self.removeVisible = ko.pureComputed(function() {
    if (ko.isObservable(params.readOnly)) {
      return !params.readOnly();
    } else {
      return !params.readOnly;
    }
  });

  self.upload.init();
};
