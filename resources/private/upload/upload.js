if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

LUPAPISTE.Upload = {
    applicationId: ko.observable(),
    attachmentId: ko.observable(),
    attachmentTypeGroups: ko.observableArray(),
    selectedType: ko.observable(),
    defaultType: ko.observable(),
    errorMessage: ko.observable()
};

LUPAPISTE.Upload.setModel = function(applicationId, attachmentId, selectedType, defaultType, errorMessage) {
  LUPAPISTE.Upload.applicationId(applicationId);
  LUPAPISTE.Upload.attachmentId(attachmentId);
  LUPAPISTE.Upload.selectedType(selectedType);
  LUPAPISTE.Upload.defaultType(defaultType ? defaultType : undefined); // Empty string -> undefined
  LUPAPISTE.Upload.errorMessage(errorMessage);
  debug(applicationId, attachmentId, selectedType, defaultType, errorMessage);
};

LUPAPISTE.Upload.loadTypes = function(applicationId) {
  if (applicationId) {
    ajax.query("attachment-types",{id: applicationId})
    .success(function(d) {
      LUPAPISTE.Upload.attachmentTypeGroups(d.typeGroups);
      ko.applyBindings(LUPAPISTE.Upload, $("#attachmentUploadForm")[0]);
    })
    .call();
  }
};

LUPAPISTE.Upload.init = function(applicationId, attachmentId, defaultType) {
  LUPAPISTE.Upload.setModel(applicationId, attachmentId, defaultType, defaultType);
  LUPAPISTE.Upload.loadTypes(applicationId);
};

LUPAPISTE.Upload.initFromURLParams = function() {
  if (location.search) {
    var applicationId = pageutil.getURLParameter("applicationId");
    LUPAPISTE.Upload.setModel(applicationId,
      pageutil.getURLParameter("attachmentId"),
      pageutil.getURLParameter("type"),
      pageutil.getURLParameter("defaultType"),
      pageutil.getURLParameter("errorMessage"));

    LUPAPISTE.Upload.loadTypes(applicationId);
  }
};

$(LUPAPISTE.Upload.initFromURLParams);
