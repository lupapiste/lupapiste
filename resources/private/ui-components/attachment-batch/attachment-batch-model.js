LUPAPISTE.AttachmentBatchModel = function( params ) {
  "use strict";
  var self = this;
  params = _.defaults( params, {dropZone: "#application-attachments-tab",
                                files: ko.observableArray(),
                                buttonClass: "positive caps",
                                buttonText: "attachment.add-multiple",
                                allowMultiple: true,
                                doNotInitialize: true});
  ko.utils.extend( self, new LUPAPISTE.FileUploadModel( params ));

  self.badFiles = ko.observableArray();
  self.progress = ko.observable();

  function process() {
    self.listenService( "badFile", function( event ) {
      self.badFiles.push( _.pick( event, ["message", "file"]));
    });
    self.listenService( "filesUploadingProgress", function( event ) {
      self.progress( event.progress );
    });
  }

  self.init( process );
};
