LUPAPISTE.YesNoDialogModel = function (params) {
  "use strict";
  var self = this;

  self.text = params.text;

  self.ok = function() {
    hub.send("close-dialog");
  };
};
