// If owner argument is given, it should be a ComponentBaseModel
// instance. The UploadModel then adds itself to owner's dispose
// queue.
// Parameters [optional]:
//  [files]: Observable array.
//  [allowMultiple]: Whether multiple files can be uploaded at the
//  same time (false).
//  [readOnly]: If true only the file listing is shown (false).
//  [dropZone]: Dropzone selector as string (default null, no dropzone).
//  [badFileHandler]. Function to be called on bad files (size,
//  type). If not given, errors are displayed with indicators. Event
//  argument contains (localized) message and file.
//  [errorHandler]. Function to be called on server errors. If not
//  given, the errors are displayed with indicators. The event
//  argument contains generic localized message.
LUPAPISTE.UploadModel = function( owner, params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.fileUploadService;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  if( owner ) {
    owner.addToDisposeQueue( self );
  }

  self.files = params.files || ko.observableArray();
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

  function notifyService( message, data ) {
    self.sendEvent( service.serviceName,
                    message,
                    _.merge( data, {input: self.fileInputId}));
  }

  function indicatorError( event ) {
    hub.send("indicator", {
      style: "negative",
      rawMessage: event.message
    });
  }

  function bindToService() {
    service.bindFileInput({maximumUploadSize: 100000000, // 100 MB
                           id: self.fileInputId,
                           dropZone: params.dropZone,
                           allowMultiple: params.allowMultiple});
    self.listenService( "filesUploaded", function( event ) {
      if( event.status === "success" ) {
        self.files(_.concat( self.files(), event.files));
        // Since the basic file upload jQuery plugin does not support
        // limiting drag'n'drop to only one file, we prune the files
        // array, if needed.
        if( !self.allowMultiple ) {
          self.files( [_.last( self.files())]);
        }
      } else {
        (params.errorHandler || indicatorError)({
          message: loc( "attachment.upload.failure",
                        event.message )});
      }
    });

    self.listenService( "filesUploading", function( event ) {
      self.waiting( event.state !== "finished" );
    });
    self.listenService( "badFile", params.badFileHandler || indicatorError);
  }



  self.removeFile = function( data ) {
    self.files( _.filter( self.files(),
                          function( file ) {
                            return file.fileId !== data.fileId;
                          }));
    notifyService( "removeFile", {attachmentId: data.fileId});
  };



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
