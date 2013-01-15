var attachment = (function() {
  "use strict";

  var applicationId;
  var attachmentId;
  var commentModel = new comments.create();
  var authorizationModel = authorization.create();
  var approveModel = new ApproveModel(authorizationModel);

  function ApproveModel(authorizationModel) {
    var self = this;

    self.authorizationModel = authorizationModel;

    self.setApplication = function(application) { self.application = application; };
    self.setAuthorizationModel = function(authorizationModel) { self.authorizationModel = authorizationModel; };
    self.setAttachmentId = function(attachmentId) { self.attachmentId = attachmentId; };

    self.stateIs = function(state) {
      var att = self.application &&
        _.find(self.application.attachments,
            function(attachment) {
              return attachment.id === self.attachmentId;
          });
      return att.state === state;
    };

    self.isNotOk = function() { return !self.stateIs('ok');};
    self.doesNotRequireUserAction = function() { return !self.stateIs('requires_user_action');};
    self.isApprovable = function() { return self.authorizationModel.ok('approve-attachment'); };
    self.isRejectable = function() { return self.authorizationModel.ok('reject-attachment'); };

    self.rejectAttachment = function() {
      var id = self.application.id;
      ajax.command("reject-attachment", { id: id, attachmentId: self.attachmentId})
        .success(function(d) {
          notify.success("liite hyl\u00E4tty",model);
          repository.reloadApplication(id);
        })
        .call();
      return false;
    };

    self.approveAttachment = function() {
      var id = self.application.id;
      ajax.command("approve-attachment", { id: id, attachmentId: self.attachmentId})
        .success(function(d) {
          notify.success("liite hyv\u00E4ksytty",model);
          repository.reloadApplication(id);
        })
        .call();
      return false;
    };
  }

  var model = {
    attachmentId:   ko.observable(),
    application: {
      id:     ko.observable(),
      title:  ko.observable()
    },
    filename:       ko.observable(),
    latestVersion:  ko.observable(),
    versions:       ko.observable(),
    name:           ko.observable(),
    type:           ko.observable(),
    attachmentType: ko.observable(),
    allowedAttahmentTypes: ko.observableArray(),

    hasPreview: function() {
      return this.isImage() || this.isPdf() || this.isPlainText();
    },

    isImage: function() {
      var version = this.latestVersion();
      if (!version) return false;
      var contentType = version.contentType;
      return contentType && contentType.indexOf('image/') === 0;
    },

    isPdf: function() {
      var version = this.latestVersion();
      if (!version) return false;
      return version.contentType === "application/pdf";
    },

    isPlainText: function() {
      var version = this.latestVersion();
      if (!version) return false;
      return version.contentType === "text/plain";
    },

    newAttachmentVersion: function() {
      initFileUpload(this.application.id(), this.attachmentId(), this.attachmentType(), false);

      // Upload dialog is opened manually here, because click event binding to
      // dynamic content rendered by Knockout is not possible
      LUPAPISTE.ModalDialog.open("#upload-dialog");
    }
  };

  model.attachmentType.subscribe(function(attachmentType) {
    var type = model.type();
    var prevAttachmentType = type["type-group"] + "." + type["type-id"];
    if (prevAttachmentType != attachmentType) {
      ajax
        .command("set-attachment-type",
          {id:              model.application.id(),
           attachmentId:    model.attachmentId(),
           attachmentType:  attachmentType})
        .success(function(e) {
          debug("Updated attachmentType:", e);
        })
        .call();
    }
  });

  function showAttachment(application) {
    if (!applicationId || !attachmentId) return;
    var attachment = _.filter(application.attachments, function(value) {return value.id === attachmentId;})[0];
    if (!attachment) {
      error("Missing attachment: application:", applicationId, "attachment:", attachmentId);
      return;
    }

    model.latestVersion(attachment.latestVersion);
    model.versions(attachment.versions);
    model.filename(attachment.filename);
    model.type(attachment.type);

    var type = attachment.type["type-group"] + "." + attachment.type["type-id"];
    model.attachmentType(type);
    model.name("attachmentType." + type);
    model.allowedAttahmentTypes(application.allowedAttahmentTypes);

    model.application.id(applicationId);
    model.application.title(application.title);
    model.attachmentId(attachmentId);

    commentModel.setApplicationId(application.id);
    commentModel.setTarget({type: "attachment", id: attachmentId});
    commentModel.setComments(application.comments);

    approveModel.setApplication(application);
    approveModel.setAttachmentId(attachmentId);

    authorizationModel.refresh(application);
  }

  hub.onPageChange("attachment", function(e) {
    applicationId = e.pagePath[0];
    attachmentId = e.pagePath[1];
    hub.send("load-application", {id: applicationId});
  });

  hub.subscribe("application-loaded", function(data) {
    var app = data.applicationDetails.application;
    if (applicationId === app.id) showAttachment(app);
  });

  function resetUploadIframe() {
    var originalUrl = $("#uploadFrame").attr("data-src");
    $("#uploadFrame").attr("src", originalUrl);
  }

  hub.subscribe("upload-cancelled", LUPAPISTE.ModalDialog.close);

  hub.subscribe({type: "dialog-close", id : "upload-dialog"}, function(e) {
    resetUploadIframe();
  });

  function toApplication() {
    window.location.href = "#!/application/" + model.application.id();
  }

  $(function() {
    ko.applyBindings({
      attachment: model,
      approve: approveModel,
      authorization: authorizationModel,
      comment: commentModel
    }, $("#attachment")[0]);

    // Iframe content must be loaded AFTER parent JS libraries are loaded.
    // http://stackoverflow.com/questions/12514267/microsoft-jscript-runtime-error-array-is-undefined-error-in-ie-9-while-using
    resetUploadIframe();
  });

  var uploadingApplicationId;

  function uploadDone() {
    if (uploadingApplicationId) {
      repository.reloadApplication(uploadingApplicationId);
      LUPAPISTE.ModalDialog.close();
      uploadingApplicationId = null;
    }
  }

  hub.subscribe("upload-done", uploadDone);

  function newAttachment(m) {
    initFileUpload(m.application.id(), null, null, true);
  }

  function initFileUpload(applicationId, attachmentId, attachmentType, typeSelector) {
    uploadingApplicationId = applicationId;
    var iframeId = 'uploadFrame';
    var iframe = document.getElementById(iframeId);
    if (iframe) {
      if (iframe.contentWindow.LUPAPISTE && typeof iframe.contentWindow.LUPAPISTE.Upload.init === "function") {
        iframe.contentWindow.LUPAPISTE.Upload.init(applicationId, attachmentId, attachmentType, typeSelector);
      } else {
        error("LUPAPISTE.Upload.init is not a function");
      }
    } else {
      error("IFrame not found ", iframeId);
    }
  }

  return { newAttachment: newAttachment };

})();
