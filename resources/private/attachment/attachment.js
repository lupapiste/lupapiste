/**
 * attachment.js:
 */

var attachment = function() {

  var applicationId;
  var attachmentId;
  var model;

  function createModel() {
    return {
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
  }

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
  }

  hub.subscribe({type: "page-change", pageId: "attachment"}, function(e) {
    applicationId = e.pagePath[0];
    attachmentId = e.pagePath[1];
    repository.getApplication(applicationId, showAttachment);
  });

  hub.subscribe("repository-application-reload", function(e) {
    if (applicationId === e.applicationId) {
      repository.getApplication(applicationId, showAttachment);
    }
  });

  function resetUploadIframe() {
    var originalUrl = $("#uploadFrame").attr("src");
    $("#uploadFrame").attr("src", originalUrl);
    LUPAPISTE.ModalDialog.close();
  }

  hub.subscribe("upload-done", function(e) {
    repository.reloadAllApplications(resetUploadIframe);
  });

  hub.subscribe("upload-cancelled", function(e) {
    ajax.command("delete-empty-attachment", { id: e.applicationId, attachmentId: e.attachmentId})
    .success(function(d) {
      repository.reloadAllApplications(resetUploadIframe);
    })
    .call();
  });

  function toApplication(){
    window.location.href="#!/application/"+ model.application.id();
  }

  $(function() {
    model = createModel();
    ko.applyBindings(model, $("#attachment")[0]);
  });

  function newAttachment(m) {
    ajax.command("create-attachment", {id:  m.id()})
    .success(function(d) {
      repository.reloadAllApplications(function() {
        var iframe = $("#uploadFrame").contents();
        iframe.find("#applicationId").val(d.applicationId);
        iframe.find("#attachmentId").val(d.attachmentId);
      });
    })
    .call();
  }

  return { newAttachment: newAttachment };

}();
