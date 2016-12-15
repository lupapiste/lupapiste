// Params [optional]:
//  upload: UploadModel instance
//  [buttonIcon]: Icon for add button (default lupicon-circle-plus)
//  [buttonText]: Ltext for the button ('application.attachmentsAdd')
//  [buttonClass]: Button classes (positive). In addition, the button always has btn class.
LUPAPISTE.UploadButtonModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.upload = params.upload;

  // Setting for attribute to "" effectively disables file selection.
  self.labelFor = self.disposedComputed( function() {
    return self.upload.waiting()
      || self.upload.readOnly ? "" : self.upload.fileInputId;
  });

  self.options = _.defaults( _.pick( params,
                                     ["buttonIcon", "buttonText"]),
                             {buttonIcon: "lupicon-circle-plus",
                              buttonText: "application.attachmentsAdd",
                              buttonClass: "btn "
                              + _.get(params, "buttonClass", "positive")});
};
