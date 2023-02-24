LUPAPISTE.TextareaDialogModel = function (params) {
  "use strict";
  var self = this;

  self.text = params.ltext ? loc(params.ltext) : params.text;

  self.yesTitle = params.lyesTitle ? loc(params.lyesTitle) : params.yesTitle;

  self.noTitle = params.lnoTitle ? loc(params.lnoTitle) : params.noTitle;

  self.yes = params.yesFn || _.noop;

  self.no = params.noFn || _.noop;

  self.textarea = params.textarea;
  self.textarea.label = params.textarea.llabel ? loc(params.textarea.llabel) : params.textarea.label;
};
