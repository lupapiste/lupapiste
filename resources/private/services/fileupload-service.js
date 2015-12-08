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

  $("#" + self.fileInputId).fileupload({
    url: "/api/upload/file",
    type: "POST",
    dataType: "json",
    formData: [
      { name: "__anti-forgery-token", value: $.cookie("anti-csrf-token") }
    ],
    start: function(e, data) {
      hub.send("fileuploadService::filesUploading", {state: "pending"});
    },
    always: function(e, data) {
      hub.send("fileuploadService::filesUploading", {state: "finished"});
    },
    done: function(e, data) {
      hub.send("fileuploadService::filesUploaded", {status: "success",
                                                    files: data.result.files});
    },
    fail: function(e, data) {
      hub.send("fileuploadService::filesUploaded", {status: "failed"});
    },
    progress: function (e, data) {
      var progress = parseInt(data.loaded / data.total * 100, 10);
      hub.send("fileuploadService::filesUploadingProgress", {progress: progress});
    }
  });

  hub.subscribe("fileuploadService::uploadFile", function() {
    $("#fileupload-input").click();
  });

  hub.subscribe("fileuploadService::removeFile", function() {
    ajax.command("remove-uploaded-file", {attachmentId: 1}).call();
  });
};
