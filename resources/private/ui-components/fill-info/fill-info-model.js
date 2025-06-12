LUPAPISTE.FillInfoModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );
  var attachmentsService = lupapisteApp.services.attachmentsService;

  self.showAttachmentsOption = self.disposedComputed( function() {
    return params.documentName === "tyonjohtaja-v2"
        && attachmentsService.authModel.ok( "copy-user-attachments-to-application" );
  });

  self.authorization = params.documentAuthModel;
  self.fillAttachments = ko.observable(self.showAttachmentsOption());

  // Disable if already processing a command to avoid
  // concurrently invoking the attachments service
  self.processing = attachmentsService.processing;

  self.fillUserInfo = function () {
    ajax
      .command("set-current-user-to-document", params)
      .success(function () {
        repository.load(params.id);
        hub.send("documentChangedInBackend", {
          docId: params.documentId
        });
      })
      .processing(self.processing)
      .call();

    if (self.fillAttachments()) {
      attachmentsService.copyUserAttachments({});
    }
  };
};
