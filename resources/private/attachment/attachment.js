/**
 * attachment.js:
 */

var attachment = function() {

  var applicationId;
  var attachmentId;
  var commentModel = new comments.create();
  var authorizationModel = authorization.create();
  var approveModel = new ApproveModel(authorizationModel);

  function ApproveModel(authorizationModel) {
    var self = this;

    self.application;
    self.authorizationModel = authorizationModel;
    self.attachmentId;

    self.setApplication = function(application) { self.application = application; };
    self.setAuthorizationModel = function(authorizationModel) { self.authorizationModel = authorizationModel; };
    self.setAttachmentId = function(attachmentId) { self.attachmentId = attachmentId; };

    // todo: move this to domain-js?
    self.stateIs = function(state) { return self.application && self.application.attachments[self.attachmentId].state === state; }

    self.isApprovable = function() { return self.authorizationModel.ok('approve-attachment') && !self.stateIs('ok'); };
    self.isRejectable = function() { return self.authorizationModel.ok('reject-attachment') && !self.stateIs('requires_user_action'); };

    self.rejectAttachment = function() {
      ajax.command("reject-attachment", { id: self.application.id, attachmentId: self.attachmentId})
        .success(function(d) {
          notify.success("liite hyl\u00E4tty",model);
          repository.reloadAllApplications();
        })
        .call();
      return false;
    };

    self.approveAttachment = function() {
      ajax.command("approve-attachment", { id: self.application.id, attachmentId: self.attachmentId})
        .success(function(d) {
          notify.success("liite hyv\u00E4ksytty",model);
          repository.reloadAllApplications();
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
    type:           ko.observable(),

    isImage: function() {
      var contentType = this.latestVersion().contentType;
      return contentType && contentType.indexOf('image/') === 0;
    },

    isPdf: function() {
      return this.latestVersion().contentType === "application/pdf";
    }
  };

  function showAttachment(application) {
    if (!applicationId || !attachmentId) return;
    var attachment = application.attachments && application.attachments[attachmentId];
    if (!attachment) {
      error("Missing attachment: application:", applicationId, "attachment:", attachmentId);
      return;
    }

    model.latestVersion(attachment.latestVersion);
    model.versions(attachment.versions);
    model.filename(attachment.filename);
    model.type(attachment.type);
    model.application.id(applicationId);
    model.application.title(application.title);

    commentModel.setApplicationId(application.id);
    commentModel.setTarget({type: "attachment", id: attachmentId});
    commentModel.setComments(application.comments);

    approveModel.setApplication(application);
    approveModel.setAttachmentId(application.id);

    authorizationModel.refresh(application);
  }

  hub.onPageChange("attachment", function(e) {
    applicationId = e.pagePath[0];
    attachmentId = e.pagePath[1];
    repository.getApplication(applicationId, showAttachment);
  });

  hub.subscribe("repository-application-reload", function(data) {
    var app = data.application;
    if (applicationId === app.id) showAttachment(app);
  });

  function resetUploadIframe() {
    var originalUrl = $("#uploadFrame").attr("data-src");
    $("#uploadFrame").attr("src", originalUrl);
  }

  hub.subscribe("upload-done", LUPAPISTE.ModalDialog.close);
  hub.subscribe("upload-cancelled", LUPAPISTE.ModalDialog.close);

  hub.subscribe({type: "dialog-close", id : "upload-dialog"}, function(e) {
    resetUploadIframe();
    repository.reloadAllApplications();
  });

  function toApplication(){
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

  function newAttachment(m) {
    var iframeId = 'uploadFrame';
    var iframe = document.getElementById(iframeId);
    if (iframe) {
      if (iframe.contentWindow.LUPAPISTE
          && typeof iframe.contentWindow.LUPAPISTE.Upload.init === "function") {
        iframe.contentWindow.LUPAPISTE.Upload.init(m.id(), undefined);
      } else {
        error("LUPAPISTE.Upload.init is not a function");
      }
    } else {
      error("IFrame not found ", iframeId);
    }
  }

  return { newAttachment: newAttachment };

}();
