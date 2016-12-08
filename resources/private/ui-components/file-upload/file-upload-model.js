// Convenient file upload mechanism that utilises the FileuploadService.
// Params [optional]:
//  [buttonIcon]: Icon for add button (default lupicon-circle-plus)
//  [buttonText]: Ltext for the button ('application.attachmentsAdd')
//  files: ObservableArray for the files.
//  [readOnly]: If true only the file listing is shown (false).
//  [dropZone]: Dropzone selector as string (default null).
LUPAPISTE.FileUploadModel = function( params ) {
  "use strict";
  var self = this;
  var base = new LUPAPISTE.ComponentBaseModel();
  var service = lupapisteApp.services.fileUploadService;

  ko.utils.extend( self, base);

  self.params = _.defaults( params, {buttonIcon: "lupicon-circle-plus",
                                     buttonText: "application.attachmentsAdd"});
  self.files = params.files;
  self.fileInputId = _.uniqueId( "file-input-id-" );
  self.waiting = ko.observable();

  // Setting for attribute to "" effectively disables file selection.
  self.labelFor = self.disposedComputed( function() {
    return self.waiting() || params.readOnly ? "" : self.fileInputId;
  });

  self.listenService = function ( message, fn ) {
    self.addEventListener( service.serviceName, message, function( event ) {
      if( event.input === self.fileInputId ) {
        fn( event );
      }
    });
  };

  self.process = function() {
    self.listenService( "filesUploaded", function( event ) {
          self.files(_.concat( self.files(), event.files));
     });

    self.listenService( "filesUploading", function( event ) {
      self.waiting( event.state !== "finished" );
    });
    self.listenService( "badFile", function( event ) {
      hub.send("indicator", {
        style: "negative",
        message: event.message
      });
    });
  };

  function bindToService() {
    service.bindFileInput({maximumUploadSize: 100000000, // 100 MB
                           id: self.fileInputId,
                           dropZone: params.dropZone});
    self.process();
  }

  if( !params.readOnly ) {
    // Trick to ensure that rendering is done before binding.
    _.defer(bindToService);
  }

  self.removeFile = function( data ) {
    self.files( _.filter( self.files(),
                          function( file ) {
                            return file.fileId !== data.fileId;
                          }));
  };


  self.dispose = function() {
    self.sendEvent( service.serviceName,
                    "destroy",
                    {fileInputId: self.fileInputId});
    base.dispose();
  };
};
