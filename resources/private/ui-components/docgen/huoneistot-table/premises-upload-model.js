LUPAPISTE.PremisesUploadModel = function( params ) {
  "use strict";
  var self = this;

  self.doc = params.doc;
  self.applicationId = params.applicationId;
  self.allowed = lupapisteApp.models.applicationAuthModel.ok( "upload-premises-data" );

  self.disabled = ko.observable(false);
  self.pending = ko.observable(false);

  self.submit = function(form) {
    var formData = new FormData(form);
    formData.append("id", self.applicationId);
    formData.append("doc", self.doc);
    $.ajax({
      type: "POST",
      url: "/api/raw/upload-premises-data",
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
      success: function(res) {
        if (res.ok) {
          repository.load(self.applicationId);
          if (_.isFunction(params.onSuccess)) {
            params.onSuccess(res);
          }
        } else {
          util.showSavedIndicator(res);
        }
      },
      error: function(res) {
        hub.send("indicator", {style: "negative", message: res.responseJSON.text});
      },
      complete: function() {
        self.pending(false);
        form.reset();
      }
    });
  };

  self.chooseFile = function(data, event) {
    $(event.target).closest("form").find("input[name='files[]']").trigger("click");
  };

  self.fileChanged = function(data, event) {
    $(event.target).closest("form").submit();
  };

};
