LUPAPISTE.OkButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.okTitle = params.okTitle || "ok";

  self.ok = function() {
    if (_.isFunction(params.okFn)) {
      params.okFn();
    }
    hub.send("close-dialog");
  };
};
