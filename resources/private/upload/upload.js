var LUPAPISTE = LUPAPISTE || {};
LUPAPISTE.Upload = {
  fileExtensions: LUPAPISTE.config.fileExtensions.join(", "),
  applicationId: ko.observable(),
  attachmentId: ko.observable(),
  attachmentType: ko.observable(),
  attachmentTypeGroups: ko.observableArray(),
  typeSelector: ko.observable(false),
  operationId: ko.observable(),
  opSelector: ko.observable(false),
  errorMessage: ko.observable(),
  targetType: ko.observable(),
  targetId: ko.observable(),
  locked: ko.observable(),
  authority: ko.observable(),
  selectableOperations: ko.observableArray(),
  selectedOperationId: ko.observable(),
  helpVisible: ko.observable(false)
};

LUPAPISTE.Upload.setModel = function(options) {
  "use strict";
  LUPAPISTE.Upload.applicationId(options.applicationId);
  LUPAPISTE.Upload.attachmentId(options.attachmentId);
  LUPAPISTE.Upload.attachmentType(options.attachmentType);
  LUPAPISTE.Upload.typeSelector(options.typeSelector ? true : false);
  LUPAPISTE.Upload.selectableOperations([{id: options.operationId}]);
  LUPAPISTE.Upload.operationId(options.operationId);
  LUPAPISTE.Upload.opSelector(options.opSelector ? true : false);
  LUPAPISTE.Upload.errorMessage(options.errorMessage);
  LUPAPISTE.Upload.targetType(options.target ? options.target.type : null);
  LUPAPISTE.Upload.targetId(options.target ? options.target.id : null);
  LUPAPISTE.Upload.locked(options.locked || false);
  LUPAPISTE.Upload.authority(options.authority || false);
};

LUPAPISTE.Upload.loadTypes = function(applicationId) {
  "use strict";

  if (applicationId) {
    ajax
      .query("attachment-types",{id: applicationId})
      .success(function(d) {
        LUPAPISTE.Upload.attachmentTypeGroups(_.map(attachmentUtils.sortAttachmentTypes(d.attachmentTypes), function(v) {
          return {group: v[0], types: _.map(v[1], function(t) {return {name: t};})};
        }));
        var uploadForm$ = $("#attachmentUploadForm");
        uploadForm$.applyBindings(LUPAPISTE.Upload);
        $("#initLoader").hide();
        uploadForm$.show();
      })
      .call();
  }
};

LUPAPISTE.Upload.loadOperations = function(applicationId) {
  "use strict";

  if (applicationId) {
    ajax
      .query("attachment-operations", {id: applicationId})
      .success(function(data) {
        if (data.operations) {
          LUPAPISTE.Upload.selectableOperations(data.operations);
          LUPAPISTE.Upload.operationId.valueHasMutated();
        }
      })
      .call();
  }
};

LUPAPISTE.Upload.init = function(options) {
  "use strict";
  LUPAPISTE.Upload.setModel(options);
  LUPAPISTE.Upload.loadTypes(options.applicationId);
  LUPAPISTE.Upload.loadOperations(options.applicationId);
};

LUPAPISTE.Upload.initFromURLParams = function() {
  "use strict";
  if (location.search) {
    var applicationId = pageutil.getURLParameter("applicationId");
    var options = {
      applicationId: applicationId,
      attachmentId: pageutil.getURLParameter("attachmentId"),
      attachmentType: pageutil.getURLParameter("attachmentType"),
      typeSelector: JSON.parse(pageutil.getURLParameter("typeSelector") || false),
      opSelector: JSON.parse(pageutil.getURLParameter("opSelector") || false),
      operationId: pageutil.getURLParameter("operationId"),
      errorMessage: pageutil.getURLParameter("errorMessage"),
      target: {type: pageutil.getURLParameter("targetType"),
               id: pageutil.getURLParameter("targetId")},
      locked: JSON.parse(pageutil.getURLParameter("locked") || false),
      authority: JSON.parse(pageutil.getURLParameter("authority") || false)
    };
    LUPAPISTE.Upload.init(options);
  }
};

$(LUPAPISTE.Upload.initFromURLParams);
