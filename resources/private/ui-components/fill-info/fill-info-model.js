LUPAPISTE.FillInfoModel = function(params) {
  "use strict";
  var self = this;

  var attachmentsService = lupapisteApp.services.attachmentsService;

  self.showAttachmentsOption = params.documentName === "tyonjohtaja-v2";
  self.authorization = params.documentAuthModel;
  self.fillAttachments = ko.observable(self.showAttachmentsOption);

  console.log(params);

  self.fillUserInfo = function () {
    ajax
      .command("set-current-user-to-document", params)
      .success(function () {
        repository.load(params.id);
        hub.send("documentChangedInBackend", {
          documentName: params.documentName
        });
      })
      .call();

    if (self.fillAttachments()) {
      attachmentsService.copyUserAttachments({});
    }
  };
};
