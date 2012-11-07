if (typeof LUPAPISTE == "undefined") {var LUPAPISTE = {};}

LUPAPISTE.Upload = {
    applicationId: ko.observable(),
    attachmentId: ko.observable(),
    attachmentTypeGroups: ko.observableArray(),
    selectedType: ko.observable(),
    defaultType: ko.observable()
};

LUPAPISTE.Upload.init = function(applicationId, attachmentId, defaultType) {
  LUPAPISTE.Upload.applicationId(applicationId);
  LUPAPISTE.Upload.attachmentId(attachmentId);
  LUPAPISTE.Upload.selectedType(defaultType);

  if (applicationId) {
    ajax.query("attachment-types",{id: applicationId})
    .success(function(d) {
      LUPAPISTE.Upload.attachmentTypeGroups(d.typeGroups);
      ko.applyBindings(LUPAPISTE.Upload, $("#attachmentUploadForm")[0]);
    })
    .call();
  }
};
