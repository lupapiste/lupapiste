if (typeof LUPAPISTE == "undefined") {var LUPAPISTE = {};}

LUPAPISTE.Upload = {
    applicationId: undefined,
    attachmentId: undefined,
    attachmentTypeGroups: undefined
};

LUPAPISTE.Upload.init = function(applicationId, attachmentId) {
  LUPAPISTE.Upload.applicationId;
  LUPAPISTE.Upload.attachmentId;
  $("#applicationId").val(applicationId ? applicationId : "");
  $("#attachmentId").val(attachmentId ? attachmentId : "");

  if (applicationId) {
    ajax.query("attachment-types",{id: applicationId})
    .success(function(d) {
      LUPAPISTE.Upload.attachmentTypeGroups = d.typeGroups;
    })
    .call();
  }
};
