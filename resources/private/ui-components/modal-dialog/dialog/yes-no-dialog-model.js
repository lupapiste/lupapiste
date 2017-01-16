LUPAPISTE.YesNoDialogModel = function (params) {
  "use strict";
  var self = this;

  self.text = params.ltext ? loc(params.ltext) : params.text;

  self.yesTitle = params.lyesTitle ? loc(params.lyesTitle) : params.yesTitle;

  self.noTitle = params.lnoTitle ? loc(params.lnoTitle) : params.noTitle;

  self.yes = params.yesFn || _.noop;

  self.no = params.noFn || _.noop;

  self.yesEnabled = _.isUndefined(params.yesEnabled) ? true : params.yesEnabled;

};
