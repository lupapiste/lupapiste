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
      type:           ko.observable(),
      isImage: function(contentType) {
        return contentType.substring(0,6) == 'image/'; 
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
    model.attachmentId(attachmentId);
    model.filename(attachment.filename);
    model.type(attachment.type);
    model.application.id(applicationId);
    model.application.title(application.title);
    model.newFile(null);
  }

  hub.subscribe("repository-application-reload", function(e) {
    if (applicationId === e.applicationId) {
      repository.getApplication(applicationId, showAttachment);
    }
  });

  hub.subscribe("upload-done", function(e) {
    var originalUrl = $("#uploadFrame").attr("src");
    $("#uploadFrame").attr("src", originalUrl);
    $("#uploadFrame").css("visibility", "hidden");

    repository.reloadAllApplications();
  });

  function toApplication(){
    window.location.href="#!/application/"+ model.application.id();
  }

  $(function() {
    hub.subscribe({type: "page-change", pageId: "attachment"}, function(e) {
      applicationId = e.pagePath[0];
      attachmentId = e.pagePath[1];
      repository.getApplication(applicationId, showAttachment);
    });
    
    model = createModel();
    ko.applyBindings(model, $("#attachment")[0]);
  });

  function newAttachment(m) {
    ajax.command("create-attachment", {id:  m.id()})
    .success(function(d) {
      repository.reloadAllApplications(function() {
        //window.location.hash = "!/attachment/" + d.applicationId + "/" + d.attachmentId;

        var iframe = $("#uploadFrame").contents();
        iframe.find("#applicationId").val(d.applicationId);
        iframe.find("#attachmentId").val(d.attachmentId);
        $("#uploadFrame").css("visibility", "visible");
      });
    })
    .call();
  }

  return { newAttachment: newAttachment };

}();
