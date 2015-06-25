LUPAPISTE.UploadModel = function(params) {
  "use strict";

  var self = this;

  self.disabled = ko.observable(false);
  self.pending = ko.observable(false);

  self.submit = function(form) {
    console.log("submit", form);
    var formData = new FormData(form);
    $.ajax({
        type: "POST",
        url: "/api/command/upload",
        enctype: "multipart/form-data",
        data: formData,
        cache: false,
        contentType: false,
        processData: false,
        beforeSend: function() {console.log("beforeSend");},
        success: function() {console.log("success");},
        complete: function() {console.log("complete");}
      });
  }

  self.chooseFile = function(data, event) {
    console.log("chooseFile", $(event.target).closest("form"));
    $(event.target).closest("form").find("input[name='file']").click();
  }

  self.fileChanged = function(data, event) {
    console.log("file changed", event);
    $(event.target).closest("form").submit();
  }
};
