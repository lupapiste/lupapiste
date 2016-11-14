var attachment = (function() {
  "use strict";

  var service = lupapisteApp.services.attachmentsService;

  var pageReadyForQuery = ko.observable(false);

  var applicationId = ko.observable();
  var attachmentId = ko.observable();

  var uploadingApplicationId = null;

  var signingModel = new LUPAPISTE.SigningModel("#dialog-sign-attachment", false);

  hub.onPageLoad("attachment", function() {
    attachmentId(null); // Reset attachment id to kill component, if already alive.
    pageutil.showAjaxWait();
    applicationId(pageutil.subPage());
    attachmentId(pageutil.lastSubPage());

    if (lupapisteApp.models.application._js.id !== applicationId()) {
      pageReadyForQuery(false);
      repository.load(applicationId(), undefined, undefined, true);
    } else {
      lupapisteApp.setTitle(lupapisteApp.models.application._js.title);
      pageReadyForQuery(true);
    }
  });

  hub.onPageUnload("attachment", function() {
    if (pageutil.getPage() !== "attachment") {
      pageReadyForQuery(false);
      applicationId(null);
      attachmentId(null);
    }
  });

  pageReadyForQuery.subscribe(function(ready) {
    if (ready) {
      if (!service.getAttachment(attachmentId())) {
        service.queryOne(attachmentId());
      }
      if (_.isEmpty(service.groupTypes())) {
        service.queryGroupTypes();
      }
    }
  });

  var attachment = ko.computed(function() {
    if (pageReadyForQuery() && applicationId() && attachmentId() && service.authModel.ok("attachment")) {
      var attachment = service.getAttachment(attachmentId());
      if (ko.isObservable(attachment)) {
        pageutil.hideAjaxWait();
        return attachment();
      }
    }
  });

  hub.subscribe("application-model-updated", function() {
    pageReadyForQuery(!_.isNil(attachmentId()));
  });

  hub.subscribe("upload-cancelled", LUPAPISTE.ModalDialog.close);

  hub.subscribe({eventType: "dialog-close", id : "upload-dialog"}, function() {
    resetUploadIframe();
  });

  function resetUploadIframe() {
    var originalUrl = $("#uploadFrame").attr("data-src");
    $("#uploadFrame").attr("src", originalUrl);
  }

  $(function() {
    $("#attachment").applyBindings({attachment: attachment, signingModel: signingModel});
    $("#upload-page").applyBindings({});
    $(signingModel.dialogSelector).applyBindings({signingModel: signingModel, authorization: service.authModel});

    // Iframe content must be loaded AFTER parent JS libraries are loaded.
    // http://stackoverflow.com/questions/12514267/microsoft-jscript-runtime-error-array-is-undefined-error-in-ie-9-while-using
    resetUploadIframe();
  });

  function uploadDone() {
    if (uploadingApplicationId) {
      LUPAPISTE.ModalDialog.close();
      uploadingApplicationId = null;
    }
  }

  hub.subscribe("upload-done", uploadDone);

  function initFileUpload(options) {
    uploadingApplicationId = options.applicationId;
    var iframeId = "uploadFrame";
    var iframe = document.getElementById(iframeId);
    function tryInit( counter ) {
      if( _.get( iframe, "contentWindow.LUPAPISTE.Upload")) {
        iframe.contentWindow.LUPAPISTE.Upload.init(options);
      } else {
        if( counter ) {
          _.delay( tryInit, 100, counter-- );
        }
      }
    }
    // Keep trying for 10 seconds max (100 * 100 ms)
    tryInit( 100 );
  }

  function regroupAttachmentTypeList(types) {
    return _.map(types, function(v) { return {group: v[0], types: _.map(v[1], function(t) { return {name: t}; })}; });
  }

  return {
    initFileUpload: initFileUpload,
    regroupAttachmentTypeList: regroupAttachmentTypeList
  };

})();
