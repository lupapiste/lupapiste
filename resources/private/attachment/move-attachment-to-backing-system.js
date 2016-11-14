(function() {
  "use strict";

  var pageName  = "move-attachments-to-backing-system-select";
  var eventName = "start-moving-attachments-to-backing-system";
  var command   = "move-attachments-to-backing-system";
  var multiSelect = _.extend(new LUPAPISTE.AttachmentMultiSelect(), {});

  multiSelect.hash = "!/" + pageName + "/";

  var doMoveAttachmets = function(selectedAttachmentsIds) {
    var id = multiSelect.model.appModel.id();
    ajax.command(command, {
      id: id,
      lang: loc.getCurrentLanguage(),
      attachmentIds: selectedAttachmentsIds
    })
    .success(function() {
      var permit = externalApiTools.toExternalPermit(multiSelect.model.appModel._js);
      repository.load(id);
      multiSelect.model.appModel.open("attachments");
      if (multiSelect.model.appModel.externalApi.enabled()) {
        hub.send("external-api::integration-sent", permit);
      }
    })
    .error(function() {
      notify.error(loc("error.dialog.title"), loc("application.attachmentsMoveToBackingSystem.error"));
      repository.load(id);
    })
    .call();
  };

  multiSelect.model.moveAttachmets = function(selectedAttachmentsIds) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.attachmentsMoveToBackingSystem"),
      loc("application.attachmentsMoveToBackingSystem.confirmationMessage"),
      {title: loc("yes"), fn: _.partial(doMoveAttachmets, selectedAttachmentsIds)},
      {title: loc("no")}
    );
  };

  hub.onPageLoad(pageName, function() {
    multiSelect.onPageLoad();
  });

  hub.onPageUnload(pageName, function() {
    multiSelect.onPageUnload();
  });

  hub.subscribe(eventName, function() {
    multiSelect.subscribe();
  });

  $(function() {
    $("#" + pageName).applyBindings(multiSelect.model);
  });
})();
