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

  var mimeTypeRegex = new RegExp(LUPAPISTE.config.mimeTypePattern);
  var MAXIMUM_UPLOAD_SIZE = 15000000; // 15Mb

  $("#" + self.fileInputId).fileupload({
    url: "/api/raw/upload-file",
    type: "POST",
    dataType: "json",
    formData: [
      { name: "__anti-forgery-token", value: $.cookie("anti-csrf-token") }
    ],
    add: function(e, data) {
      var mimeTypeLegal = mimeTypeRegex.test(data.files[0].type);
      if(mimeTypeLegal && data.files[0].size <= MAXIMUM_UPLOAD_SIZE) {
        data.submit();
      } else {
        var message = !mimeTypeLegal ?
          "error.illegal-file-type" :
          "error.bulletins.illegal-upload-size";

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

  hub.subscribe("fileuploadService::uploadFile", function() {
    $("#fileupload-input").click();
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
