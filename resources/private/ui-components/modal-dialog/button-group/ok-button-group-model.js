LUPAPISTE.OkButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.okTitle = util.getIn(params, ["okTitle"]) || loc("ok");

  var okFn = util.getIn(params, ["okFn"]);

  self.ok = function() {
    if (_.isFunction(okFn)) {
      okFn();
    }
    hub.send("close-dialog");
  };
};
