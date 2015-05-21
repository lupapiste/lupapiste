LUPAPISTE.OkDialogModel = function (params) {
  "use strict";
  var self = this;

  self.text = params.text;

  self.ok = params.okFn || function() { _.noop(); };
};
