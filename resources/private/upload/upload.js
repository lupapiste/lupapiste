if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

LUPAPISTE.Upload = {
    applicationId: ko.observable(),
    attachmentId: ko.observable(),
    attachmentType: ko.observable(),
    attachmentTypeGroups: ko.observableArray(),
    typeSelector: ko.observable(false),
    errorMessage: ko.observable()
};

LUPAPISTE.Upload.setModel = function(applicationId, attachmentId, attachmentType, typeSelector, errorMessage) {
  "use strict";
  LUPAPISTE.Upload.applicationId(applicationId);
  LUPAPISTE.Upload.attachmentId(attachmentId);
  LUPAPISTE.Upload.attachmentType(attachmentType);
  LUPAPISTE.Upload.typeSelector(typeSelector ? true : false);
  LUPAPISTE.Upload.errorMessage(errorMessage);
};

LUPAPISTE.Upload.loadTypes = function(applicationId) {
  "use strict";
  if (applicationId) {
    ajax
      .query("attachment-types",{id: applicationId})
      .success(function(d) {
        LUPAPISTE.Upload.attachmentTypeGroups(_.map(d.attachmentTypes, function(v) {
          return {group: v[0], types: _.map(v[1], function(t) { return {name: t}; })};
        }));
        ko.applyBindings(LUPAPISTE.Upload, $("#attachmentUploadForm")[0]);
      })
      .call();
  }
};

LUPAPISTE.Upload.init = function(applicationId, attachmentId, attachmentType, typeSelector) {
  "use strict";
  LUPAPISTE.Upload.setModel(applicationId, attachmentId, attachmentType, typeSelector, null);
  LUPAPISTE.Upload.loadTypes(applicationId);
};

LUPAPISTE.Upload.initFromURLParams = function() {
  "use strict";
  if (location.search) {
    var applicationId = pageutil.getURLParameter("applicationId");
    LUPAPISTE.Upload.setModel(applicationId,
      pageutil.getURLParameter("attachmentId"),
      pageutil.getURLParameter("attachmentType"),
      pageutil.getURLParameter("typeSelector"),
      pageutil.getURLParameter("errorMessage"));
    LUPAPISTE.Upload.loadTypes(applicationId);
  }
};

$(LUPAPISTE.Upload.initFromURLParams);
