LUPAPISTE.YesNoButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.yesTitle = params.yesTitle || loc("yes");
  self.noTitle = params.noTitle || loc("no");

  self.yes = function() {
    if (_.isFunction(params.yesFn)) {
      params.yesFn();
    }
    hub.send("close-dialog");
  };

  self.no = function() {
    if (_.isFunction(params.noFn)) {
      params.noFn();
    }
    hub.send("close-dialog");
  };
};
