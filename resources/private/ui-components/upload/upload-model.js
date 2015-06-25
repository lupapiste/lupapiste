LUPAPISTE.UploadModel = function(params) {
  "use strict";

  var self = this;

  self.disabled = ko.observable(false);

  self.pending = ko.observable(false);

  self.submit = function(form) {
    var formData = new FormData(form);
    $.ajax({
        type: "POST",
        url: "/api/upload/organization-area",
        enctype: "multipart/form-data",
        data: formData,
        cache: false,
        contentType: false,
        processData: false,
        beforeSend: function(request) {
          self.pending(true);
          _.each(self.headers, function(value, key) { request.setRequestHeader(key, value); });
          request.setRequestHeader("x-anti-forgery-token", $.cookie("anti-csrf-token"));
        },
        success: function() {
          // TODO
        },
        complete: function() {
          self.pending(false);
        }
      });
  }

  self.chooseFile = function(data, event) {
    $(event.target).closest("form").find("input[name='files[]']").click();
  }

  self.fileChanged = function(data, event) {
    $(event.target).closest("form").submit();
  }
};
