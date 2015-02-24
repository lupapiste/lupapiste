LUPAPISTE.FillInfoModel = function(params) {
  "use strict";
  var self = this;

  self.fillUserInfo = function () {
    ajax
      .command("set-user-to-document", params)
      .success(function () {
        repository.load(params.id);
        hub.send("documentChangedInBackend", {
          documentName: params.documentName
        });
      })
      .call();
  };
};

