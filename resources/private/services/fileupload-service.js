LUPAPISTE.FileuploadService = function() {
  "use strict";
  var self = this;

  self.fileInputId = util.randomElementId("fileupload-input");

  if (!document.getElementById(self.fileInputId)) {
    var input = document.createElement("input");
    input.className = "hidden";
    input.type = "file";
    input.name = "files[]";
    input.setAttribute("multiple", true);
    input.setAttribute("id", self.fileInputId);
    document.body.appendChild(input);
  }

  var fileExtensionRegex = new RegExp("(?:\\.([^.]+))?$");
  var MAXIMUM_UPLOAD_SIZE = 15000000; // 15Mb

  function getFileExtension(fname) {
    var result = fileExtensionRegex.exec(fname)[1];
    return _.isString(result) ? result.toLowerCase() : "";
  }

  $("#" + self.fileInputId).fileupload({
    url: "/api/raw/upload-file",
    type: "POST",
    dataType: "json",
    formData: [
      { name: "__anti-forgery-token", value: $.cookie("anti-csrf-token") }
    ],
    add: function(e, data) {
      var acceptedFile = _.includes(LUPAPISTE.config.fileExtensions, getFileExtension(data.files[0].name));

      // IE9 doesn't have size, submit data and check files in server
      var size = util.getIn(data, ["files", 0, "size"], 0);
      if(acceptedFile && size <= MAXIMUM_UPLOAD_SIZE) {
        data.submit();
      } else {
        var message = !acceptedFile ?
          "error.file-upload.illegal-file-type" :
          "error.file-upload.illegal-upload-size";

        hub.send("indicator", {
          style: "negative",
          message: message
        });
      }
    },
    start: function() {
      hub.send("fileuploadService::filesUploading", {state: "pending"});
    },
    always: function() {
      hub.send("fileuploadService::filesUploading", {state: "finished"});
    },
    done: function(e, data) {
      hub.send("fileuploadService::filesUploaded", {status: "success",
                                                    files: data.result.files});
    },
    fail: function(e, data) {
      hub.send("fileuploadService::filesUploaded", {
        status: "failed",
        message: data.jqXHR.responseJSON.text
      });
    },
    progress: function (e, data) {
      var progress = parseInt(data.loaded / data.total * 100, 10);
      hub.send("fileuploadService::filesUploadingProgress", {progress: progress});
    }
  });

  hub.subscribe("fileuploadService::removeFile", function(event) {
    ajax
      .command("remove-uploaded-file", {attachmentId: event.attachmentId})
      .success(function(res) {
        hub.send("fileuploadService::fileRemoved", {attachmentId: res.attachmentId});
      })
      .call();
  });
};
