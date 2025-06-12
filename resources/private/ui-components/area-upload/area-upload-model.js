LUPAPISTE.AreaUploadModel = function(params) {
  "use strict";

  var self = this;

  var applicationMode = params.mode === "application";

  self.enabled = applicationMode
    ? lupapisteApp.models.applicationAuthModel.okObservable( "upload-application-shapefile")
    : lupapisteApp.models.globalAuthModel.okObservable( "pseudo-organization-area" );

  self.pending = ko.observable(false);

  self.errorMessage = ko.observable(false);

  self.successMessage = ko.observable(false);

  self.submit = function(form) {
    var formData = new FormData(form);
    if( applicationMode ) {
      formData.append("id", lupapisteApp.models.application.id());
    } else {
      formData.append("organizationId", lupapisteApp.usagePurpose().orgId);
    }
    $.ajax({
      type: "POST",
      url: sprintf( "/api/raw/%s",
                    applicationMode ? "upload-application-shapefile"  : "organization-area" ),
      enctype: "multipart/form-data",
      data: formData,
      cache: false,
      contentType: false,
      processData: false,
      beforeSend: function(request) {
        self.pending(true);
        self.errorMessage(false);
        self.successMessage(false);
        _.each(self.headers, function(value, key) { request.setRequestHeader(key, value); });
        request.setRequestHeader("x-anti-forgery-token", $.cookie("anti-csrf-token"));
      },
      success: function(res) {
        if (res.ok) {
          self.successMessage("upload.success");
          if (_.isFunction(params.onSuccess)) {
            params.onSuccess(res);
          }
        } else {
          self.errorMessage(res.text || loc("error.upload-failed"));
        }
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
