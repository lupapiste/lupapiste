(function() {
  "use strict";

  var pageName  = "move-attachments-to-case-management-select";
  var eventName = "start-moving-attachments-to-case-management";
  var command   = "attachments-to-asianhallinta";
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
      multiSelect.model.appModel.open("attachments");
      repository.load(id);
    })
    .error(function() {
      notify.error(loc("error.dialog.title"), loc("application.attachmentsMoveToCaseManagement.error"));
      repository.load(id);
    })
    .call();
  };

  multiSelect.model.moveAttachmets = function(selectedAttachmentsIds) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.attachmentsMoveToCaseManagement"),
      loc("application.attachmentsMoveToCaseManagement.confirmationMessage"),
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
