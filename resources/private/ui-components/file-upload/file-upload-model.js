// Convenient file upload mechanism that utilises the FileuploadService.
// Params [optional]:
//  [buttonIcon]: Icon for add button (default lupicon-circle-plus)
//  [buttonText]: Ltext for the button ('application.attachmentsAdd')
//  [buttonClass]: Button classes (positive). In addition, the button always has btn class.
//  [allowMultiple]: Whether multiple files can be uploaded at the same time (false).
//  files: ObservableArray for the files.
//  [readOnly]: If true only the file listing is shown (false).
//  [dropZone]: Dropzone selector as string (default null, no dropzone).
//  [doNotInitialize]: If true, the init function is not called automatically (false).
LUPAPISTE.FileUploadModel = function( params ) {
  "use strict";
  var self = this;
  var base = new LUPAPISTE.ComponentBaseModel();
  var service = lupapisteApp.services.fileUploadService;

  ko.utils.extend( self, base);

  self.files = params.files;
  self.fileInputId = _.uniqueId( "file-input-id-" );
  self.waiting = ko.observable();
  self.readOnly = params.readOnly;

  // Setting for attribute to "" effectively disables file selection.
  self.labelFor = self.disposedComputed( function() {
    return self.waiting() || self.readOnly ? "" : self.fileInputId;
  });

  self.buttonOptions = _.defaults( _.pick( params,
                                           ["buttonIcon", "buttonText",
                                            "readOnly", "allowMultiple"]),
                                   {buttonIcon: "lupicon-circle-plus",
                                    buttonText: "application.attachmentsAdd",
                                    buttonClass: "btn "
                                    + _.get(params, "buttonClass", "positive"),
                                    readOnly: false,
                                    allowMultiple: false,
                                    fileInputId: self.fileInputId,
                                    waiting: self.waiting,
                                    labelFor: self.labelFor});

  self.listenService = function ( message, fn ) {
    self.addEventListener( service.serviceName, message, function( event ) {
      if( event.input === self.fileInputId ) {
        fn( event );
      }
    });
  };

  function baseProcess() {
    self.listenService( "filesUploaded", function( event ) {
      self.files(_.concat( self.files(), event.files));
      // Since the basic file upload jQuery plugin does not support
      // limiting drag'n'drop to only on file, we prune the files
      // arrary, if needed.
      if( !params.allowMultiple ) {
        self.files( [_.last( self.files())]);
      }
     });

    self.listenService( "filesUploading", function( event ) {
      self.waiting( event.state !== "finished" );
    });
    //self.process();
  };

  function badFileIndicator() {
    self.listenService( "badFile", function( event ) {
      hub.send("indicator", {
        style: "negative",
        message: event.message
      });
    });
  };

  function bindToService( extraFun ) {
    service.bindFileInput({maximumUploadSize: 100000000, // 100 MB
                           id: self.fileInputId,
                           dropZone: params.dropZone,
                           allowMultiple: params.allowMultiple});
    baseProcess();
    extraFun();
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

  self.init = function( extraFun ) {
    if( !self.readOnly ) {
      // Trick to ensure that rendering is done before binding.
      _.defer(bindToService, extraFun || _.noop);
    }
  };

  if( !params.doNotInitialize ) {
    self.init( badFileIndicator );
  }
};
