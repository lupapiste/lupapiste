/**
 * attachment.js:
 */

var attachment = function() {

  var applicationId;
  var attachmentId;
  var commentModel = new CommentModel();

  function CommentModel() {
    var self = this;

    self.text = ko.observable();

    self.disabled = ko.computed(function() { return _.isEmpty(self.text());});

    self.submit = function(model) {
      var applicationId = application.id();
      ajax.command("add-comment", { id: applicationId, text: model.text(), target: { type: "application"}})
        .success(function(d) {
          repository.reloadAllApplications();
          model.text("");
        })
        .call();
      return false;
    }
  };


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
    comments:       ko.observableArray(),
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
    model.comments(ko.mapping.fromJS(application.comments));
  }

  hub.onPageChange("attachment", function(e) {
    applicationId = e.pagePath[0];
    attachmentId = e.pagePath[1];
    repository.getApplication(applicationId, showAttachment);
  });

  hub.subscribe("repository-application-reload", function(app) {
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
