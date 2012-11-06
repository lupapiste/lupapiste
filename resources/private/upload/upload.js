if (typeof LUPAPISTE == "undefined") {var LUPAPISTE = {};}

LUPAPISTE.Upload = {
    applicationId: ko.observable(),
    attachmentId: ko.observable(),
    attachmentTypeGroups: ko.observableArray(),
    selectedType: ko.observable()
};

LUPAPISTE.Upload.init = function(applicationId, attachmentId, selectedType) {
  LUPAPISTE.Upload.applicationId(applicationId);
  LUPAPISTE.Upload.attachmentId(attachmentId);
  LUPAPISTE.Upload.selectedType(selectedType);

  if (applicationId) {
    ajax.query("attachment-types",{id: applicationId})
    .success(function(d) {
      LUPAPISTE.Upload.attachmentTypeGroups(d.typeGroups);
      ko.applyBindings(LUPAPISTE.Upload, $("#attachmentUploadForm")[0])
    })
    .call();
  }
};
