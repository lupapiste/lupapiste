LUPAPISTE.YesNoButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.yesTitle = params.yesTitle || loc("yes");
  self.noTitle = params.noTitle || loc("no");

  self.yesEnabled = params.yesEnabled || true;

  self.yes = function() {
    hub.send("close-dialog");
    if (_.isFunction(params.yesFn)) {
      params.yesFn();
    }
  };

  self.no = function() {
    hub.send("close-dialog");
    if (_.isFunction(params.noFn)) {
      params.noFn();
    }
  };
};
