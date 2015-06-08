LUPAPISTE.YesNoDialogModel = function (params) {
  "use strict";
  var self = this;

  self.text = params.ltext ? loc(params.ltext) : params.text;

  self.yes = params.yesFn || function() { _.noop(); };

  self.no = params.noFn || function() { _.noop(); };
};
