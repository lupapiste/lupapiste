LUPAPISTE.OkDialogModel = function (params) {
  "use strict";
  var self = this;

  self.text = params.ltext ? loc(params.ltext) : params.text;

  // Due to legacy reasons, HTML is the default format.
  self.isHtml = _.isBoolean( params.html ) ? params.html : true;

  self.okTitle = params.okTitle;

  self.ok = params.okFn || _.noop;

};
