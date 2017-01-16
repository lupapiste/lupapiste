// Params [optional]:
//  upload: UploadModel instance
//  [buttonIcon]: Icon for add button (default lupicon-circle-plus)
//  [buttonText]: Ltext for the button ('application.attachmentsAdd')
//  [buttonClass]: Button classes (positive). In addition, the button always has btn class.
//  [testId]: test id prefix for input and label (upload-button -> upload-button-input, upload-button-label).
LUPAPISTE.UploadButtonModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.BaseUploadModel(params));

  self.options = _.defaults( _.pick( params,
                                     ["buttonIcon", "buttonText", "buttonClass", "testId"]),
                             {buttonIcon: "lupicon-circle-plus",
                              buttonText: "application.attachmentsAdd",
                              buttonClass: "btn positive",
                              testId: "upload-button"});
};
