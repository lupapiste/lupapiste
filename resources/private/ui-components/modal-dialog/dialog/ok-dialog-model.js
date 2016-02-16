LUPAPISTE.OkDialogModel = function (params) {
  "use strict";
  var self = this;

  self.text = params.ltext ? loc(params.ltext) : params.text;

  self.okTitle = params.okTitle;

  self.ok = params.okFn || _.noop;
};
