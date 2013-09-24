var LUPAPISTE = LUPAPISTE || {};
LUPAPISTE.Upload = {
  fileExtensions: LUPAPISTE.config.fileExtensions.join(", "),
  applicationId: ko.observable(),
  attachmentId: ko.observable(),
  attachmentType: ko.observable(),
  attachmentTypeGroups: ko.observableArray(),
  typeSelector: ko.observable(false),
  errorMessage: ko.observable(),
  targetType: ko.observable(),
  targetId: ko.observable(),
  locked: ko.observable(),
  authority: ko.observable()
};

LUPAPISTE.Upload.setModel = function(applicationId, attachmentId, attachmentType, typeSelector, errorMessage, target, locked, authority) {
  "use strict";
  LUPAPISTE.Upload.applicationId(applicationId);
  LUPAPISTE.Upload.attachmentId(attachmentId);
  LUPAPISTE.Upload.attachmentType(attachmentType);
  LUPAPISTE.Upload.typeSelector(typeSelector ? true : false);
  LUPAPISTE.Upload.errorMessage(errorMessage);
  LUPAPISTE.Upload.targetType(target ? target.type : null);
  LUPAPISTE.Upload.targetId(target ? target.id : null);
  LUPAPISTE.Upload.locked(locked || false);
  LUPAPISTE.Upload.authority(authority || false);
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
        uploadForm$.applyBindings(LUPAPISTE.Upload);
        $("#initLoader").hide();
        uploadForm$.show();
      })
      .call();
  }
};

LUPAPISTE.Upload.init = function(applicationId, attachmentId, attachmentType, typeSelector, target, locked, authority) {
  "use strict";
  LUPAPISTE.Upload.setModel(applicationId, attachmentId, attachmentType, typeSelector, null, target, locked, authority);
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
      {type: pageutil.getURLParameter("targetType"), id: pageutil.getURLParameter("targetId")},
      pageutil.getURLParameter("locked"),
      pageutil.getURLParameter("authority"));
    LUPAPISTE.Upload.loadTypes(applicationId);
  }
};

$(LUPAPISTE.Upload.initFromURLParams);
