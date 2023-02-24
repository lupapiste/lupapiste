var attachment = (function() {
  "use strict";

  var service = lupapisteApp.services.attachmentsService;

  var pageReadyForQuery = ko.observable(false);

  var applicationId = ko.observable();
  var attachmentId = ko.observable();

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


  $(function() {
    $("#attachment").applyBindings({attachment: attachment, signingModel: signingModel});
    $(signingModel.dialogSelector).applyBindings({signingModel: signingModel, authorization: service.authModel});

  });

  function regroupAttachmentTypeList(types) {
    return _.map(types, function(v) { return {group: v[0], types: _.map(v[1], function(t) { return {name: t}; })}; });
  }

  return {
    regroupAttachmentTypeList: regroupAttachmentTypeList
  };

})();
