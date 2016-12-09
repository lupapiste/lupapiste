LUPAPISTE.FileuploadService = function() {
  "use strict";
  var self = this;

  self.serviceName = "fileuploadService";

  function hubscribe( event, fun ) {
    hub.subscribe( sprintf( "%s::%s", self.serviceName, event),
                   fun );
  }

  function prepareDropZone( dropZone ) {
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
                  sel.addClass( "drop-zone--highlight");
                  if( !latest ) {
                    clear( -1 );
                  }
                  latest++;
                });
        return sel;
      }
    })();
  }

  self.bindFileInput = function( options ) {

    options = _.defaults( options, {
      maximumUploadSize: 15000000, // 15 MB
      id: util.randomElementId("fileupload-input")
    });

    var fileInputId = options.id;

    if (!document.getElementById(self.fileInputId)) {
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

    var $dropZone = prepareDropZone( options.dropZone );

    $("#" + fileInputId).fileupload({
      url: "/api/raw/upload-file",
      type: "POST",
      dataType: "json",
      formData: [
        { name: "__anti-forgery-token", value: $.cookie("anti-csrf-token") }
      ],
      dropZone: $dropZone,
      maxNumberOfFiles: options.allowMultiple ? undefined : 1,
      add: function(e, data) {
        var file = _.get( data, "files.0", {});
        var acceptedFile = _.includes(LUPAPISTE.config.fileExtensions,
                                      getFileExtension(file.name));

        // IE9 doesn't have size, submit data and check files in server
        var size = file.size || 0;
        if(acceptedFile && size <= options.maximumUploadSize) {
          data.submit();
        } else {
          hubSend( "badFile",
                   {message: acceptedFile
                    ? "error.file-upload.illegal-upload-size"
                    : "error.file-upload.illegal-file-type",
                    file: file}
                 );
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
                                  files: data.result.files});
      },
      fail: function(e, data) {
        hubSend("filesUploaded", {
          status: "failed",
          message: data.jqXHR.responseJSON.text
        });
      },
      progress: function (e, data) {
        var progress = parseInt(data.loaded / data.total * 100, 10);
        hubSend("filesUploadingProgress", {progress: progress});
      }
    });

    hubscribe( "destroy", function( event )  {
      if( event.fileInputId === fileInputId ) {
        $("#" + fileInputId ).fileupload( "destroy");
        if( $dropZone ) {
          $dropZone.off();
        }
      }
    } );

    return fileInputId;
  };

  hubscribe("removeFile", function(event) {
    ajax
      .command("remove-uploaded-file", {attachmentId: event.attachmentId})
      .success(function(res) {
        hub.send("fileuploadService::fileRemoved", {attachmentId: res.attachmentId});
      })
      .call();
  });
};
