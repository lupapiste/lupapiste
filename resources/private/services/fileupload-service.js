LUPAPISTE.FileuploadService = function() {
  "use strict";
  var self = this;

  self.serviceName = "fileuploadService";

  function prepareDropZone( dropZone, fileInputId ) {
    return (function () {
        var latest = 0;
        if( dropZone ) {
          var sel = $( dropZone );
          var clear = function( count ) {
            if( count === latest ) {
              sel.removeClass( "drop-zone--highlight");
              latest = 0;
            } else {
              _.delay( clear, 100, latest );
            }
          };

          sel.on( "dragover",
                  function()  {
                    if( !$("#" + fileInputId).attr( "disabled") ) {
                      sel.addClass( "drop-zone--highlight");
                      if( !latest ) {
                        clear( -1 );
                      }
                      latest++;
                    }
                  });
          return sel;
        }
    })();
  }

  self.bindFileInput = function( options, pluginOptions ) {

    options = _.defaults( options, {
      id: util.randomElementId("fileupload-input"),
      allowMultiple: true
    });

    var fileInputId = options.id;
    var hubscriptions = [];
    var connections = [];  // jqXHRs

    function hubscribe( message, fun ) {
      hubscriptions.push( hub.subscribe( sprintf( "%s::%s",
                                                  self.serviceName,
                                                  message),
                                         function( event ) {
                                           if( event.input === fileInputId ) {
                                             fun( event );
                                           }
                                         }));
    }

    if (!document.getElementById(fileInputId)) {
      var input = document.createElement("input");
      input.className = "hidden";
      input.type = "file";
      input.name = "files[]";
      input.setAttribute("multiple", true);
      input.setAttribute("id", fileInputId);
      document.body.appendChild(input);
    }

    var fileExtensionRegex = new RegExp("(?:\\.([^.]+))?$");

    function getFileExtension(fname) {
      var result = fileExtensionRegex.exec(fname)[1];
      return _.isString(result) ? result.toLowerCase() : "";
    }

    function hubSend( message, event ) {
      hub.send( self.serviceName + "::" + message,
                _.defaults( event, {input: fileInputId}));
    }

    var $dropZone = prepareDropZone( options.dropZone, fileInputId );
    var isAuthenticated = Boolean(util.getIn(lupapisteApp, ["models","currentUser","username"]));
    var apiUrl = isAuthenticated ? "/api/raw/upload-file-authenticated" : "/api/raw/upload-file";
    var formData = [ { name: "__anti-forgery-token", value: $.cookie("anti-csrf-token") } ];
    var applicationId = util.getIn(lupapisteApp, ["models", "application", "id"]);
    if (applicationId) {
      formData.push({ name: "id", value: applicationId });
    }

    $("#" + fileInputId).fileupload(_.defaults(pluginOptions, {
      url: apiUrl,
      type: "POST",
      dataType: "json",
      formData: formData,
      dropZone: $dropZone,
      limitConcurrentUploads: 10,
      add: function(e, data) {
        var file = _.get( data, "files.0", {});
        var maximumUploadSize = isAuthenticated ? LUPAPISTE.config.loggedInUploadMaxSize : LUPAPISTE.config.anonymousUploadMaxSize;
        // if allowMultiple is false, accept only first of the given files (in case of drag and dropping multiple files)
        if (options.allowMultiple || _.indexOf(data.originalFiles, file) === 0) {
          var acceptedFile = _.includes(LUPAPISTE.config.fileExtensions,
                                        getFileExtension(file.name));
          // IE9 doesn't have size, submit data and check files in server
          var size = file.size || 0;
          if (!acceptedFile) {
            hubSend( "badFile", {message: loc("error.file-upload.illegal-file-type"), file: file, error: "illegal-file-type"});
          } else if (size > maximumUploadSize) {
            hubSend( "badFile", {message: loc("error.file-upload.illegal-upload-size", maximumUploadSize/1000000), file: file, error: "illegal-file-size"});
          } else {
            hubSend( "fileAdded", {file: file});
            connections.push( data.submit() );
          }
        }
      },
      start: function() {
        hubSend("filesUploading", {state: "pending"});
      },
      always: function() {
        hubSend("filesUploading", {state: "finished"});
      },
      done: function(e, data) {
        hubSend("filesUploaded", {status: "success",
                                  files: data.result.files,
                                  originalFiles: data.originalFiles});
      },
      fail: function(e, data) {
        hubSend("filesUploaded", {
          status: "failed",
          message: data.textStatus ||
            (data.jqXHR.responseJSON && data.jqXHR.responseJSON.text) ||
            "",
          errorKey: (data.jqXHR.responseJSON && data.jqXHR.responseJSON.error) ||
            "error.unknown"
        });
      },
      progress: function (e, data) {
        var progress = parseInt(data.loaded / data.total * 100, 10);
        hubSend("filesUploadingProgress", {progress: progress,
                                           loaded: data.loaded,
                                           total: data.total,
                                           file: _.get(data, "files.0")});
      },
      drop: function(e) {
        if (!options.dropZone ) {
          e.preventDefault();
        }
      }
    }));

    hubscribe( "destroy", function()  {
      try {
        $("#" + fileInputId ).fileupload( "destroy");
      } catch( err ) {}

      if( $dropZone ) {
        $dropZone.off();
      }
      _.each( hubscriptions, hub.unsubscribe );
      hubscriptions = [];
    } );

    hubscribe( "cancel", function() {
      _.filter( connections, function( conn ) {
        conn.abort();
        return false;
      });
    });

    hubscribe( "toggle-enabled", function( event ) {
      var $input = $("#" + fileInputId);
      $input.fileupload( event.enabled ? "enable" : "disable");
      $input.attr( "disabled", !event.enabled );
    });

    return fileInputId;
  };

  hub.subscribe( self.serviceName + "::removeFile", function(event) {
    ajax
      .command("remove-uploaded-file", {attachmentId: event.attachmentId})
      .success(function(res) {
        hub.send("fileuploadService::fileRemoved",
                 {attachmentId: res.attachmentId,
                  input: event.input});
      })
      .call();
  });
};
