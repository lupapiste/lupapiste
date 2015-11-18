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
    url: "/upload/file",
    type: "POST",
    dataType: "json",
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
    }
  });

  hub.subscribe("fileuploadService::uploadFile", function() {
    $("#fileupload-input").click();
  });
};
