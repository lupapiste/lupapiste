LUPAPISTE.AttachmentBatchModel = function() {
  "use strict";
  var self = this;

  var ajaxWaiting = ko.observable();

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  function badFileHandler( event ) {
    self.badFiles.push( _.pick( event, ["message", "file"]));
  }

  self.upload = new LUPAPISTE.UploadModel( self,
                                         {dropZone: "#application-attachments-tab",
                                          allowMultiple: true,
                                          errorHandler: badFileHandler});

  self.buttonOptions = { buttonClass: "positive caps",
                         buttonText: "attachment.add-multiple",
                         upload: self.upload };

  self.badFiles = ko.observableArray();

  self.upload.init();

  self.waiting = self.disposedPureComputed( function() {
    return self.upload.waiting() || ajaxWaiting();
  });
};
