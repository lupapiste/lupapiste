// If owner argument is given, it should be a ComponentBaseModel
// instance. The UploadModel then adds itself to owner's dispose
// queue.
// Parameters [optional]:
//  [files]: Observable array.
//  [allowMultiple]: Whether multiple files can be uploaded at the
//  same time (false).
//  [readOnly]: If true only the file listing is shown. If it is
//  observable, the upload is enabled accordingly. This is useful for
//  example, when the auth model is initialized belatedly (default
//  false).
//  [dropZone]: Dropzone selector as string (default null, no
//  dropzone). The dropzone highlight is done with drop-zone
//  component.
//  [badFileHandler]. Function to be called on bad files (size,
//  type). If not given, errors are displayed with indicators. Event
//  argument contains (localized) message and file.
//  [errorHandler]. Function to be called on server errors. If not
//  given, the errors are displayed with indicators. The event
//  argument contains generic localized message.
//
// Note: the init must have been called before the first upload.
LUPAPISTE.UploadModel = function( owner, params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.fileUploadService;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  if( owner && _.isFunction(owner.addToDisposeQueue)) {
    owner.addToDisposeQueue( self );
  }
  var originalFiles;
  if (params.files) {
    originalFiles = _.clone(ko.unwrap(params.files));
  }
  self.files = params.files || ko.observableArray();
  self.files.extend( {rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});
  self.fileInputId = _.uniqueId( "file-input-id-" );
  self.waiting = params.waiting || ko.observable();
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
    service.bindFileInput({id: self.fileInputId,
                           dropZone: params.dropZone,
                           allowMultiple: params.allowMultiple});
    self.listenService( "filesUploaded", function( event ) {
      if( event.status === "success" ) {
        // Since the basic file upload jQuery plugin does not support
        // limiting drag'n'drop to only one file, we prune the files
        // array, if needed.
        var fileName = event.files[0].filename;
        var originalFile = _.filter(event.originalFiles, { "name" : fileName});
        var originalPosition = _.indexOf(event.originalFiles, originalFile[0]);
        event.files[0].position = originalPosition;
        var allFiles = _.concat( self.files(), event.files);
        self.files(_.sortBy((self.allowMultiple ? allFiles : allFiles.splice(-1)), ["position"]));
      } else {
        notifyService( "fileCleared", {} );
        (params.errorHandler || indicatorError)({
          message: loc( event.errorKey || "attachment.upload.failure",
                        event.message )});
      }
    });

    self.listenService( "filesUploading", function( event ) {
      self.waiting( event.state !== "finished" );
    });
    self.listenService( "badFile", params.badFileHandler || indicatorError);

    if( ko.isObservable( self.readOnly )) {
      self.disposedComputed( function() {
        self.setEnabled( !self.readOnly());
      });
    }
  }

  // Removes file from files but not from server.
  self.clearFile = function( fileId ) {
    if (!_.isEmpty( self.files.remove( function( file ) {
      return file.fileId === fileId;
    }))) {
      notifyService( "fileCleared", {fileId: fileId});
    }
  };

  // Remove file from files and server.
  self.removeFile = function( data ) {
    self.clearFile( data.fileId );
    notifyService( "removeFile", {attachmentId: data.fileId});
  };

  // Toggle enabled/disabled
  self.setEnabled = function( enabled ) {
    notifyService( "toggle-enabled", {enabled: enabled} );
  };


  self.cancel = function() {
    notifyService( "cancel" );
    if (originalFiles) {
      self.files(originalFiles);
    } else {
      self.files.removeAll();
    }
  };

  self.init = function() {
    var alwaysReadOnly = !ko.isObservable( self.readOnly) && self.readOnly;
    if( !alwaysReadOnly ) {
      // Trick to ensure that rendering is done before binding.
      _.defer(bindToService );
    }
  };

  var baseDispose = _.bind( self.dispose, self );

  self.dispose = function() {
    self.cancel();
    notifyService( "destroy" );
    baseDispose();
  };
};
