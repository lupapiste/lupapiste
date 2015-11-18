LUPAPISTE.FileUploadService = function() {
  "use strict";
  var self = this;

  $("#fileupload-input").fileupload({
    url: "/upload/file",
    type: "POST",
    dataType: "json",
    done: function(e, data) {
      console.log("done", data.result.files);
      hub.send("fileUploadService::filesUploaded", {files: data.result.files});
    }
  });

  hub.subscribe("fileUploadService::uploadFile", function() {

    $("label[for=fileupload-input]").click();
  });
};

$(function() {
  "use strict";

  var input = document.createElement("input");
  // input.className = "hidden";
  input.type = "file";
  input.name = "files[]";
  input.setAttribute("multiple", true);
  input.setAttribute("id", "fileupload-input");

  var label = document.createElement("label");
  label.setAttribute("for", "fileupload-input");
  label.textContent = "foobar";
  document.body.insertBefore(input, document.body.firstChild);
  document.body.insertBefore(label, input);
  label.appendChild(input);
});
