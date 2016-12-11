// If owner argument is given, it should be a ComponentBaseModel instance. The UploadModel then adds itself to owner's dispose queue.
// Parameters [optional]:
//  [allowMultiple]: Whether multiple files can be uploaded at the same time (false).
//  [readOnly]: If true only the file listing is shown (false).
//  [dropZone]: Dropzone selector as string (default null, no dropzone).
//  [errorHandler]. Function to be called on errors. If not given, the errors are display with indicators.
LUPAPISTE.UploadModel = function( owner, params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.fileUploadService;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  if( owner ) {
    owner.addToDisposeQueue( self );
  }
  self.files = ko.observableArray();
  self.fileInputId = _.uniqueId( "file-input-id-" );
  self.waiting = ko.observable();
  self.readOnly = params.readOnly;
  self.allowMultiple = params.allowMultiple;

  self.listenService = function ( message, fn ) {
    self.addEventListener( service.serviceName, message, function( event ) {
      if( event.input === self.fileInputId ) {
        fn( event );
      }
    });
  };

  function indicatorError( event ) {
    hub.send("indicator", {
      style: "negative",
      message: event.message
    });
  }

  function bindToService() {
    service.bindFileInput({maximumUploadSize: 100000000, // 100 MB
                           id: self.fileInputId,
                           dropZone: params.dropZone,
                           allowMultiple: params.allowMultiple});
    self.listenService( "filesUploaded", function( event ) {
      self.files(_.concat( self.files(), event.files));
      // Since the basic file upload jQuery plugin does not support
      // limiting drag'n'drop to only on file, we prune the files
      // arrary, if needed.
      if( !self.allowMultiple ) {
        self.files( [_.last( self.files())]);
      }
    });

    self.listenService( "filesUploading", function( event ) {
      self.waiting( event.state !== "finished" );
    });
    self.listenService( "badFile", params.errorHandler || indicatorError);

  }

  self.removeFile = function( data ) {
    self.files( _.filter( self.files(),
                          function( file ) {
                            return file.fileId !== data.fileId;
                          }));
  };

  function notifyService( message ) {
    self.sendEvent( service.serviceName,
                    message,
                    {fileInputId: self.fileInputId});
  }

  self.dispose = _.wrap( "destroy", notifyService );

  self.cancel = function() {
    notifyService( "cancel" );
    self.files.removeAll();
  };

  self.init = function() {
    if( !self.readOnly ) {
      // Trick to ensure that rendering is done before binding.
      _.defer(bindToService );
    }
  };
};
