var LUPAPISTE = LUPAPISTE || {};
LUPAPISTE.Upload = {
  fileExtensions: LUPAPISTE.config.fileExtensions.join(", "),
  applicationId: ko.observable(),
  attachmentId: ko.observable(),
  attachmentType: ko.observable(),
  attachmentTypeGroups: ko.observableArray(),
  typeSelector: ko.observable(false),
  errorMessage: ko.observable(),
  target: ko.observable()
};

LUPAPISTE.Upload.setModel = function(applicationId, attachmentId, attachmentType, typeSelector, errorMessage, target) {
  "use strict";
  LUPAPISTE.Upload.applicationId(applicationId);
  LUPAPISTE.Upload.attachmentId(attachmentId);
  LUPAPISTE.Upload.attachmentType(attachmentType);
  LUPAPISTE.Upload.typeSelector(typeSelector ? true : false);
  LUPAPISTE.Upload.errorMessage(errorMessage);
  LUPAPISTE.Upload.target(target);
};

LUPAPISTE.Upload.loadTypes = function(applicationId) {
  "use strict";

  if (applicationId) {
    ajax
      .query("attachment-types",{id: applicationId})
      .success(function(d) {
        // fix for IE9 not showing the last option
        if($.browser.msie) {
          d.attachmentTypes.push(["empty", []]);
        }
        LUPAPISTE.Upload.attachmentTypeGroups(_.map(d.attachmentTypes, function(v) {
          return {group: v[0], types: _.map(v[1], function(t) { return {name: t}; })};
        }));
        var uploadForm$ = $("#attachmentUploadForm");
        ko.applyBindings(LUPAPISTE.Upload, uploadForm$[0]);
        $("#initLoader").hide();
        uploadForm$.show();
      })
      .call();
  }
};

LUPAPISTE.Upload.init = function(applicationId, attachmentId, attachmentType, typeSelector, target) {
  "use strict";
  LUPAPISTE.Upload.setModel(applicationId, attachmentId, attachmentType, typeSelector, null, target);
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
      pageutil.getURLParameter("errorMessage"),
      pageutil.getURLParameter("target"));
    LUPAPISTE.Upload.loadTypes(applicationId);
  }
};

$(LUPAPISTE.Upload.initFromURLParams);
