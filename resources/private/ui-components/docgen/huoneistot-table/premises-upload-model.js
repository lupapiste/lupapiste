LUPAPISTE.PremisesUploadModel = function( params ) {
    "use strict";
    var self = this;



    self.file = params.file || ko.observable();
    self.fileInputId = _.uniqueId( "file-input-id-" );
    self.filename = ko.observable();
    self.buttonIcon = 'lupicon-upload';
    self.buttonText = 'huoneistot.premisesUploadButton';
    self.buttonClass = 'btn positive';
    self.testId= "premises-upload-button";

    self.disabled = ko.observable(false);
    self.pending = ko.observable(false);
    self.errorMessage = ko.observable(false);
    self.successMessage = ko.observable(false);

    self.visible =

    self.submit = function(form) {
        var formData = new FormData(form);
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
        $(event.target).closest("form").find("input[name='files[]']").click();
    };

    self.fileChanged = function(data, event) {
        $(event.target).closest("form").submit();
    };

};

// repository.load kutsun jälkeen